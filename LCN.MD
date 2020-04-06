# LCN 模式

LCN模式类似为2PC的分布式解决方案，2PC先天有些缺陷，比如超时和锁的问题，所以会引入3PC，有关2PC和3PC可以详细阅读[分布式事务常用解决方案](http://www.pbteach.com/post/java_distribut/subject_dtx-02/)。LCN不是传统意义上的2PC，但是有些类似。其本质是代理数据库连接，利用切面编程，业务代码顺利执行完成得到connection提交的事务的时候并不做任何事情，而是通知TM，TM再通知每个事物单元完成事务的提交或者回滚。

## 核心类

1、启用注解类EnableDistributedTransaction，开启这个注解后框架完成了以下几个动作

序列 | 内容
---|---
1  | 启用切面LcnTransaction等
2  | 启动Netty服务
3  | 注入Tracing跟踪相关类，主要是在springcloud或dubbo RPC 请求过程中在事物单元中加入全局事务groupId等
4  | 启动日志相关类
5  | 启用数据相关类比如QueryRunner



2、核心代码LcnConnectionProxy
```
public class LcnConnectionProxy implements Connection {
    @Override
    public void commit() throws SQLException {
        //connection.commit();
    }

    @Override
    public void rollback() throws SQLException {
        //connection.rollback();
    }

    @Override
    public void close() throws SQLException {
        //connection.close();
    }
    /**
     * notify connection
     *
     * @param state transactionState
     * @return RpcResponseState RpcResponseState
     */
    public RpcResponseState notify(int state) {
        try {
            if (state == 1) {
                log.debug("commit transaction type[lcn] proxy connection:{}.", this);
                connection.commit();
            } else {
                log.debug("rollback transaction type[lcn] proxy connection:{}.", this);
                connection.rollback();
            }
            connection.close();
            log.debug("transaction type[lcn] proxy connection:{} closed.", this);
            return RpcResponseState.success;
        } catch (Exception e) {
            log.error(e.getLocalizedMessage(), e);
            return RpcResponseState.fail;
        }
    }
    .....
}
```

上面notify是收到TM的通知后真实执行的事务操作


3、DTXLogicWeaver 切面业务执行
```
    public Object runTransaction(DTXInfo dtxInfo, BusinessCallback business) throws Throwable {

        if (Objects.isNull(DTXLocalContext.cur())) {
            DTXLocalContext.getOrNew();
        } else {
            return business.call();
        }

        log.debug("<---- TxLcn start ---->");
        DTXLocalContext dtxLocalContext = DTXLocalContext.getOrNew();
        TxContext txContext;
        // ---------- 保证每个模块在一个DTX下只会有一个TxContext ---------- //
        if (globalContext.hasTxContext()) {
            // 有事务上下文的获取父上下文
            txContext = globalContext.txContext();
            dtxLocalContext.setInGroup(true);
            log.debug("Unit[{}] used parent's TxContext[{}].", dtxInfo.getUnitId(), txContext.getGroupId());
        } else {
            // 没有的开启本地事务上下文
            txContext = globalContext.startTx();
        }

        // 本地事务调用
        if (Objects.nonNull(dtxLocalContext.getGroupId())) {
            dtxLocalContext.setDestroy(false);
        }

        dtxLocalContext.setUnitId(dtxInfo.getUnitId());
        dtxLocalContext.setGroupId(txContext.getGroupId());
        dtxLocalContext.setTransactionType(dtxInfo.getTransactionType());

        // 事务参数
        TxTransactionInfo info = new TxTransactionInfo();
        info.setBusinessCallback(business);
        info.setGroupId(txContext.getGroupId());
        info.setUnitId(dtxInfo.getUnitId());
        info.setPointMethod(dtxInfo.getBusinessMethod());
        info.setPropagation(dtxInfo.getTransactionPropagation());
        info.setTransactionInfo(dtxInfo.getTransactionInfo());
        info.setTransactionType(dtxInfo.getTransactionType());
        info.setTransactionStart(txContext.isDtxStart());

        //LCN事务处理器
        try {
            return transactionServiceExecutor.transactionRunning(info);
        } finally {
            // 线程执行业务完毕清理本地数据
            if (dtxLocalContext.isDestroy()) {
                // 通知事务执行完毕
                synchronized (txContext.getLock()) {
                    txContext.getLock().notifyAll();
                }

                // TxContext生命周期是？ 和事务组一样（不与具体模块相关的）
                if (!dtxLocalContext.isInGroup()) {
                    globalContext.destroyTx();
                }

                DTXLocalContext.makeNeverAppeared();
                TracingContext.tracing().destroy();
            }
            log.debug("<---- TxLcn end ---->");
        }
    }
```

1. 生成 DTXLocalContext
2. 生成TxTransactionInfo，包含实际业务代码method

4、DTXServiceExecutor
```
    public Object transactionRunning(TxTransactionInfo info) throws Throwable {

        // 1. 获取事务类型
        String transactionType = info.getTransactionType();

        // 2. 获取事务传播状态
        DTXPropagationState propagationState = propagationResolver.resolvePropagationState(info);

        // 2.1 如果不参与分布式事务立即终止
        if (propagationState.isIgnored()) {
            return info.getBusinessCallback().call();
        }

        // 3. 获取本地分布式事务控制器
        DTXLocalControl dtxLocalControl = txLcnBeanHelper.loadDTXLocalControl(transactionType, propagationState);

        // 4. 织入事务操作
        try {
            // 4.1 记录事务类型到事务上下文
            Set<String> transactionTypeSet = globalContext.txContext(info.getGroupId()).getTransactionTypes();
            transactionTypeSet.add(transactionType);

            dtxLocalControl.preBusinessCode(info);

            // 4.2 业务执行前
            txLogger.txTrace(
                    info.getGroupId(), info.getUnitId(), "pre business code, unit type: {}", transactionType);

            // 4.3 执行业务
            Object result = dtxLocalControl.doBusinessCode(info);

            // 4.4 业务执行成功
            txLogger.txTrace(info.getGroupId(), info.getUnitId(), "business success");
            dtxLocalControl.onBusinessCodeSuccess(info, result);
            return result;
        } catch (TransactionException e) {
            txLogger.error(info.getGroupId(), info.getUnitId(), "before business code error");
            throw e;
        } catch (Throwable e) {
            // 4.5 业务执行失败
            txLogger.error(info.getGroupId(), info.getUnitId(), Transactions.TAG_TRANSACTION,
                    "business code error");
            dtxLocalControl.onBusinessCodeError(info, e);
            throw e;
        } finally {
            // 4.6 业务执行完毕
            dtxLocalControl.postBusinessCode(info);
        }
    }

```

1. 获取事务传播级别
2. 通知TM创建事务组
3. 执行业务代码
4. 事务淡远加入到事务组

5、RpcAnswerHandler & DefaultNotifiedUnitService
```
    @Override
    public Serializable execute(TransactionCmd transactionCmd) throws TxClientException {
        try {
            NotifyUnitParams notifyUnitParams = transactionCmd.getMsg().loadBean(NotifyUnitParams.class);
            // 保证业务线程执行完毕后执行事务清理操作
            TxContext txContext = globalContext.txContext(transactionCmd.getGroupId());
            if (Objects.nonNull(txContext)) {
                synchronized (txContext.getLock()) {
                    txLogger.txTrace(transactionCmd.getGroupId(), notifyUnitParams.getUnitId(),
                            "clean transaction cmd waiting for business code finish.");
                    txContext.getLock().wait();
                }
            }
            // 事务清理操作
            transactionCleanTemplate.clean(
                    notifyUnitParams.getGroupId(),
                    notifyUnitParams.getUnitId(),
                    notifyUnitParams.getUnitType(),
                    notifyUnitParams.getState());
            return true;
        } catch (TransactionClearException | InterruptedException e) {
            throw new TxClientException(e);
        }
    }
```

1. 接受TM的指定：执行事务提交还是事务回滚

6、SimpleDTXChecking，定期检查事物状态跟TM连接
```
    @Override
    public void startDelayCheckingAsync(String groupId, String unitId, String transactionType) {
        txLogger.taskTrace(groupId, unitId, "start delay checking task");
        ScheduledFuture scheduledFuture = scheduledExecutorService.schedule(() -> {
            try {
                TxContext txContext = globalContext.txContext(groupId);
                if (Objects.nonNull(txContext)) {
                    synchronized (txContext.getLock()) {
                        txLogger.taskTrace(groupId, unitId, "checking waiting for business code finish.");
                        txContext.getLock().wait();
                    }
                }
                int state = reliableMessenger.askTransactionState(groupId, unitId);
                txLogger.taskTrace(groupId, unitId, "ask transaction state {}", state);
                if (state == -1) {
                    txLogger.error(this.getClass().getSimpleName(), "delay clean transaction error.");
                    onAskTransactionStateException(groupId, unitId, transactionType);
                } else {
                    transactionCleanTemplate.clean(groupId, unitId, transactionType, state);
                    aspectLogger.clearLog(groupId, unitId);
                }

            } catch (RpcException e) {
                onAskTransactionStateException(groupId, unitId, transactionType);
            } catch (TransactionClearException | InterruptedException e) {
                txLogger.error(this.getClass().getSimpleName(), "{} clean transaction error.", transactionType);
            }
        }, clientConfig.getDtxTime(), TimeUnit.MILLISECONDS);
        delayTasks.put(groupId + unitId, scheduledFuture);
    }
```

7、DefaultNotifiedUnitService，接受TM事务通知
```
    @Override
    public Serializable execute(TransactionCmd transactionCmd) throws TxClientException {
        try {
            NotifyUnitParams notifyUnitParams = transactionCmd.getMsg().loadBean(NotifyUnitParams.class);
            // 保证业务线程执行完毕后执行事务清理操作
            TxContext txContext = globalContext.txContext(transactionCmd.getGroupId());
            if (Objects.nonNull(txContext)) {
                synchronized (txContext.getLock()) {
                    txLogger.txTrace(transactionCmd.getGroupId(), notifyUnitParams.getUnitId(),
                            "clean transaction cmd waiting for business code finish.");
                    txContext.getLock().wait();
                }
            }
            // 事务清理操作
            transactionCleanTemplate.clean(
                    notifyUnitParams.getGroupId(),
                    notifyUnitParams.getUnitId(),
                    notifyUnitParams.getUnitType(),
                    notifyUnitParams.getState());
            return true;
        } catch (TransactionClearException | InterruptedException e) {
            throw new TxClientException(e);
        }
    }
```
