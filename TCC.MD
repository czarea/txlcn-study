# TCC
TCC是Try、Confirm、Cancel三个词语的缩写，TCC要求每个分支事务实现三个操作：预处理Try、确认Confirm、撤销Cancel。Try操作做业务检查及资源预留，Confirm做业务确认操作，Cancel实现一个与Try相反的操作即回滚操作。TM首先发起所有的分支事务的try操作，任何一个分支事务的try操作执行失败，TM将会发起所有分支事务的Cancel操作，若try操作全部成功，TM将会发起所有分支事务的Confirm操作，其中Confirm/Cancel操作若执行失败，TM会进行重试。

TCC的cancel方法为业务方法加上cancel，例如业务执行方法为rpc，则为cancelRpc,TCC的cancel方法为业务方法加上confirmRpc，例如业务执行方法为rpc，则为confirmRpc

## 核心类

### TccTransactionCleanService
```
    // 受到TM通知，决定是否提交还是回滚，只想对于的cancel或者confirm方法
    @Override
    public void clear(String groupId, int state, String unitId, String unitType) throws TransactionClearException {
        Method exeMethod;
        boolean shouldDestroy = !TracingContext.tracing().hasGroup();
        try {
            TccTransactionInfo tccInfo = globalContext.tccTransactionInfo(unitId, null);
            Object object = applicationContext.getBean(tccInfo.getExecuteClass());
            // 将要移除。
            if (Objects.isNull(DTXLocalContext.cur())) {
                DTXLocalContext.getOrNew().setJustNow(true);
            }
            if (shouldDestroy) {
                TracingContext.init(Maps.of(TracingConstants.GROUP_ID, groupId, TracingConstants.APP_MAP, "{}"));
            }
            DTXLocalContext.getOrNew().setGroupId(groupId);
            DTXLocalContext.cur().setUnitId(unitId);
            exeMethod = tccInfo.getExecuteClass().getMethod(
                    state == 1 ? tccInfo.getConfirmMethod() : tccInfo.getCancelMethod(),
                    tccInfo.getMethodTypeParameter());
            try {
                exeMethod.invoke(object, tccInfo.getMethodParameter());
                log.debug("User confirm/cancel logic over.");
            } catch (Throwable e) {
                log.error("Tcc clean error.", e);
                tmReporter.reportTccCleanException(groupId, unitId, state);
            }
        } catch (Throwable e) {
            throw new TransactionClearException(e.getMessage());
        } finally {
            if (DTXLocalContext.cur().isJustNow()) {
                DTXLocalContext.makeNeverAppeared();
            }
            if (shouldDestroy) {
                TracingContext.tracing().destroy();
            }
        }
    }
```
