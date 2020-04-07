# 疑问点

## 1、事务参与方（TU）怎么知道整个事务组Id

springcloud通过Feign、RestTemplate添加interceptor实现，主要说在Http Header添加一个X-Group-ID完成
1. RestTemplateTracingTransmitter
2. FeignTracingTransmitter
3. SpringTracingApplier（SpringBoot）

### RestTemplateTracingTransmitter
```
    //拦截请求，添加Header信息
    @Override
    @NonNull
    public ClientHttpResponse intercept(
            @NonNull HttpRequest httpRequest, @NonNull byte[] bytes,
            @NonNull ClientHttpRequestExecution clientHttpRequestExecution) throws IOException {
        Tracings.transmit(httpRequest.getHeaders()::add);
        return clientHttpRequestExecution.execute(httpRequest, bytes);
    }
```

## 2、TM怎么得到全局事务信息
全局事物信息保存在redis，使用hash结构，key为tm:group: + groupId，然后接每个事务单元unitId，最终保存的是参与事务方的事务类型（LCN/TXC/TCC)，微服务modId

### RedisStorage
```
    //创建事务组，设置超时时间等
    @Override
    public void initGroup(String groupId) {
        redisTemplate.opsForHash().put(REDIS_GROUP_PREFIX + groupId, "root", "");
        redisTemplate.expire(REDIS_GROUP_PREFIX + groupId, managerConfig.getDtxTime() + 10000, TimeUnit.MILLISECONDS);
    }
    
    //事务参与方加入到事务组
    @Override
    public void saveTransactionUnitToGroup(String groupId, TransactionUnit transactionUnit) throws FastStorageException {
        if (Optional.ofNullable(redisTemplate.hasKey(REDIS_GROUP_PREFIX + groupId)).orElse(false)) {
            redisTemplate.opsForHash().put(REDIS_GROUP_PREFIX + groupId, transactionUnit.getUnitId(), transactionUnit);
            return;
        }
        throw new FastStorageException("attempts to the non-existent transaction group " + groupId,
                FastStorageException.EX_CODE_NON_GROUP);
    }
```

## 3. 事务发起方怎么通知TM
简单点通过netty长链接通信，TU启动的时候会连接到TM，后期TM新加入，TU也会收到新的TM信息，总之就说TU本地保存了所有的TM的长链接channel

### LcnStartingTransaction
```
    //DTXServiceExecutor执行完业务，finally执行postBusinessCode方法通知TM完成事务最后操作
    @Override
    public void postBusinessCode(TxTransactionInfo info) {
        // RPC close DTX group
        transactionControlTemplate.notifyGroup(
                info.getGroupId(), info.getUnitId(), info.getTransactionType(),
                DTXLocalContext.transactionState(globalContext.dtxState(info.getGroupId())));
    }
```

### TransactionControlTemplate
```
    //通知TM完成事务
    public void notifyGroup(String groupId, String unitId, String transactionType, int state) {
        try {
            txLogger.txTrace(
                    groupId, unitId, "notify group > transaction type: {}, state: {}.", transactionType, state);
            if (globalContext.isDTXTimeout()) {
                throw new LcnBusinessException("dtx timeout.");
            }
            state = reliableMessenger.notifyGroup(groupId, state);
            transactionCleanTemplate.clean(groupId, unitId, transactionType, state);
        } catch (TransactionClearException e) {
            txLogger.trace(groupId, unitId, Transactions.TE, "clean transaction fail.");
        } catch (RpcException e) {
            dtxExceptionHandler.handleNotifyGroupMessageException(Arrays.asList(groupId, state, unitId, transactionType), e);
        } catch (LcnBusinessException e) {
            // 关闭事务组失败
            dtxExceptionHandler.handleNotifyGroupBusinessException(Arrays.asList(groupId, state, unitId, transactionType), e.getCause());
        }
        txLogger.txTrace(groupId, unitId, "notify group exception state {}.", state);
    }
```

### LoopMessenger
```
    //组装NotifyGroupParams，通知TM
    @Override
    public int notifyGroup(String groupId, int transactionState) throws RpcException, LcnBusinessException {
        NotifyGroupParams notifyGroupParams = new NotifyGroupParams();
        notifyGroupParams.setGroupId(groupId);
        notifyGroupParams.setState(transactionState);
        MessageDto messageDto = request0(MessageCreator.notifyGroup(notifyGroupParams),
                clientConfig.getTmRpcTimeout() * clientConfig.getChainLevel());
        // 成功清理发起方事务
        if (!MessageUtils.statusOk(messageDto)) {
            throw new LcnBusinessException(messageDto.loadBean(Throwable.class));
        }
        return messageDto.loadBean(Integer.class);
    }
    
    // 通过RpcClient拿到TM集群其中一个连接，使用了RpcLoadBalance负载均衡的方式，默认随机
    private MessageDto request(MessageDto messageDto, long timeout, String whenNonManagerMessage) throws RpcException {
        for (int i = 0; i < rpcClient.loadAllRemoteKey().size() + 1; i++) {
            try {
                String remoteKey = rpcClient.loadRemoteKey();
                MessageDto result = rpcClient.request(remoteKey, messageDto, timeout);
                log.debug("request action: {}. TM[{}]", messageDto.getAction(), remoteKey);
                return result;
            } catch (RpcException e) {
                if (e.getCode() == RpcException.NON_TX_MANAGER) {
                    throw new RpcException(e.getCode(), whenNonManagerMessage + ". non tx-manager is alive.");
                }
            }
        }
        throw new RpcException(RpcException.NON_TX_MANAGER, whenNonManagerMessage + ". non tx-manager is alive.");
    }
```

### NettyRpcClient
```
    private MessageDto request0(RpcCmd rpcCmd, long timeout) throws RpcException {
        if (rpcCmd.getKey() == null) {
            throw new RpcException("key must be not null.");
        }
        return SocketManager.getInstance().request(rpcCmd.getRemoteKey(), rpcCmd, timeout);
    }
```

### SocketManager
```
    //有保存所有跟TM长链接的channels，然后发送请求Cmd指令
    public MessageDto request(String key, RpcCmd cmd, long timeout) throws RpcException {
        NettyRpcCmd nettyRpcCmd = (NettyRpcCmd) cmd;
        log.debug("get channel, key:{}", key);
        Channel channel = getChannel(key);
        channel.writeAndFlush(nettyRpcCmd);
        log.debug("await response");
        if (timeout < 1) {
            nettyRpcCmd.await();
        } else {
            nettyRpcCmd.await(timeout);
        }
        MessageDto res = cmd.loadResult();
        log.debug("response is: {}", res);
        nettyRpcCmd.loadRpcContent().clear();
        return res;
    }
```

## 4、TM怎么通知TU完成事务单元操作
TM通过Netty服务端的操作，在channelRead0收到TU的指令通知，然后调用所有的事务参与方，完成事务的通知

### RpcAnswerHandler
```
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcCmd cmd) {
        String remoteKey = ctx.channel().remoteAddress().toString();
        cmd.setRemoteKey(remoteKey);
        rpcClientAnswer.callback(cmd);
    }
```

### NotifyGroupExecuteService
```
    public Serializable execute(TransactionCmd transactionCmd) throws TxManagerException {
        try {
            DTXContext dtxContext = dtxContextRegistry.get(transactionCmd.getGroupId());
            // 解析参数
            NotifyGroupParams notifyGroupParams = transactionCmd.getMsg().loadBean(NotifyGroupParams.class);
            int commitState = notifyGroupParams.getState();
            // 获取事务状态（当手动回滚时会先设置状态）
            int transactionState = transactionManager.transactionStateFromFastStorage(transactionCmd.getGroupId());
            if (transactionState == 0) {
                commitState = 0;
            }

            // 系统日志
            txLogger.txTrace(
                    transactionCmd.getGroupId(), "", "notify group state: {}", notifyGroupParams.getState());

            if (commitState == 1) {
                transactionManager.commit(dtxContext);
            } else if (commitState == 0) {
                transactionManager.rollback(dtxContext);
            }
            if (transactionState == 0) {
                txLogger.txTrace(transactionCmd.getGroupId(), "", "mandatory rollback for user.");
            }
            return commitState;
        } catch (TransactionException e) {
            throw new TxManagerException(e);
        } finally {
            transactionManager.close(transactionCmd.getGroupId());
            // 系统日志
            txLogger.txTrace(transactionCmd.getGroupId(), "", "notify group successfully.");
        }
    }
```

### SimpleTransactionManager
```
    //获取所有的事务参与单元，再获取事务单元的ip和端口，通知TU
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
