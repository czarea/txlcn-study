# TM

txlcn的TM是一个比较奇怪的分布式架构，通过加入对redis的依赖，只能算是CP的分布式系统。所有全局事物信息和TU机器的一些信息保存在redis上，它自身基于netty服务端和客户端的代码，也做到初级的集群效果，
既是其中一个宕掉，整个集群还是可以用的。但是在负载均衡和一些细节的地方表现得不太完美。

## 客户端（TU）

客户端配置TM地址不用配置全部，只用配置其中一个就可以，这是TM服务端代码做的比较不一样的地方，比如开始只有A服务，有1、2、3 3个客户端，都启动完成后3个客户端是保存了A的服务地址，并且是长链接。后面加入B服务，B启动的时候从redis拿到A服务，并且通知A服务,A再通知所有跟自己连接的所有TU，TU这样就保存了所有的TM信息。

### 核心代码

1. TMAutoCluster
```
    @Override
    public void init() throws Exception {

        // 1. 通知 TC 建立连接
        List<TMProperties> tmList = fastStorage.findTMProperties().stream()
                .filter(tmProperties ->
                        !tmProperties.getHost().equals(txManagerConfig.getHost()) || !tmProperties.getTransactionPort().equals(txManagerConfig.getPort()))
                .collect(Collectors.toList());
        for (TMProperties properties : tmList) {
            NotifyConnectParams notifyConnectParams = new NotifyConnectParams();
            notifyConnectParams.setHost(txManagerConfig.getHost());
            notifyConnectParams.setPort(txManagerConfig.getPort());
            String url = String.format(MANAGER_REFRESH_URL, properties.getHost(), properties.getHttpPort());
            try {
                ResponseEntity<Boolean> res = restTemplate.postForEntity(url, notifyConnectParams, Boolean.class);
                if (res.getStatusCode().equals(HttpStatus.OK) || res.getStatusCode().is5xxServerError()) {
                    log.info("manager auto refresh res->{}", res);
                    break;
                } else {
                    fastStorage.removeTMProperties(properties.getHost(), properties.getTransactionPort());
                }
            } catch (Exception e) {
                log.error("manager auto refresh error: {}", e.getMessage());
                //check exception then remove.
                if (e instanceof ResourceAccessException) {
                    ResourceAccessException resourceAccessException = (ResourceAccessException) e;
                    if (resourceAccessException.getCause() != null && resourceAccessException.getCause() instanceof ConnectException) {
                        //can't access .
                        fastStorage.removeTMProperties(properties.getHost(), properties.getTransactionPort());
                    }
                }
            }
        }

        // 2. 保存TM 到快速存储
        if (StringUtils.hasText(txManagerConfig.getHost())) {
            TMProperties tmProperties = new TMProperties();
            tmProperties.setHttpPort(ApplicationInformation.serverPort(serverProperties));
            tmProperties.setHost(txManagerConfig.getHost());
            tmProperties.setTransactionPort(txManagerConfig.getPort());
            fastStorage.saveTMProperties(tmProperties);
        }
    }
```

2. ManagerServiceImpl
```
    @Override
    public boolean refresh(NotifyConnectParams notifyConnectParams) throws RpcException {
        List<String> keys = rpcClient.loadAllRemoteKey();
        if (keys != null && keys.size() > 0) {
            for (String key : keys) {
                rpcClient.send(key, MessageCreator.newTxManager(notifyConnectParams));
            }
        }
        return true;
    }
```

3. SimpleTransactionManager

通知TU完成事务提交或者回滚,这里代码其实是有问题的，这里通知TU的时候没有做负载均衡也没有做直接通知之前提交事务请求的哪一个TU，而是直接使用的第一个，我通过做实验，发下启动多个TU的时候是会问题的。

```
    private void notifyTransaction(DTXContext dtxContext, int transactionState) throws TransactionException {
        List<TransactionUnit> transactionUnits = dtxContext.transactionUnits();
        log.debug("group[{}]'s transaction units: {}", dtxContext.getGroupId(), transactionUnits);
        for (TransactionUnit transUnit : transactionUnits) {
            NotifyUnitParams notifyUnitParams = new NotifyUnitParams();
            notifyUnitParams.setGroupId(dtxContext.getGroupId());
            notifyUnitParams.setUnitId(transUnit.getUnitId());
            notifyUnitParams.setUnitType(transUnit.getUnitType());
            notifyUnitParams.setState(transactionState);
            txLogger.txTrace(dtxContext.getGroupId(), notifyUnitParams.getUnitId(), "notify {}'s unit: {}",
                    transUnit.getModId(), transUnit.getUnitId());
            try {
                List<String> modChannelKeys = rpcClient.remoteKeys(transUnit.getModId());
                if (modChannelKeys.isEmpty()) {
                    // record exception
                    throw new RpcException("offline mod.");
                }
                MessageDto respMsg =
                        rpcClient.request(modChannelKeys.get(0), MessageCreator.notifyUnit(notifyUnitParams));
                if (!MessageUtils.statusOk(respMsg)) {
                    // 提交/回滚失败的消息处理
                    List<Object> params = Arrays.asList(notifyUnitParams, transUnit.getModId());
                    rpcExceptionHandler.handleNotifyUnitBusinessException(params, respMsg.loadBean(Throwable.class));
                }
            } catch (RpcException e) {
                // 提交/回滚通讯失败
                List<Object> params = Arrays.asList(notifyUnitParams, transUnit.getModId());
                rpcExceptionHandler.handleNotifyUnitMessageException(params, e);
            } finally {
                txLogger.txTrace(dtxContext.getGroupId(), notifyUnitParams.getUnitId(), "notify unit over");
            }
        }
    }
```
