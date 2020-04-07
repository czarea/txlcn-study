# TXC
txc是一种柔性分布式事务，通过回滚的时候消息补偿，达到事务最终的一致性。也就说在TU提交事物前，通过监听提交的sql，查询出本次提交影响的数据，然后获取影响的数据id和字段等信息保存到H2，事务回滚的时候通过查询H2之前保存的sql记录，获取反向的sql，然后执行，达到最终的事务回滚的效果。

## 核心类

### TxcTransactionResourceProxy
```
    //类似LCN，也说代理connection，但是这里的connection是在sql执行和commit的时候做了额外的工作
    @Override
    public Connection proxyConnection(ConnectionCallback connectionCallback) throws Throwable {
        return connectionHelper.proxy(connectionCallback.call());
    }
```

### PreparedStatementWrapper
```
    // PreparedStatementWrapper是上面connection创建，在execute执行的时候，插入影响数据到H2
    @Override
    public boolean execute() throws SQLException {
        SQLException e = null;
        long start = System.nanoTime();
        try {
            eventListener.onBeforeExecute(statementInformation);
            return delegate.execute();
        } catch (SQLException sqle) {
            e = sqle;
            throw e;
        } finally {
            eventListener.onAfterExecute(statementInformation, System.nanoTime() - start, e);
        }
    }
```

### TxcJdbcEventListener
```
    //调用QueryRunner，ProtostuffSerializer 插入影响数据到H2
    @Override
    public void onAfterExecute(PreparedStatementInformation statementInformation, long timeElapsedNanos, SQLException e) {
        if (statementInformation.getAttachment() instanceof Insert) {
            try {
                sqlExecuteInterceptor.postInsert(statementInformation);
            } catch (SQLException e1) {
                throw new RuntimeException(e1);
            }
        }
    }
```

### TxcTransactionCleanService
```
    //收到TM通知回滚，TransactionCleanTemplate调用该方法，最终到TxcServiceImpl的undo执行回滚
    @Override
    public void clear(String groupId, int state, String unitId, String unitType) throws TransactionClearException {
        boolean rethrowTxcException = false;
        try {
            // 若需要回滚读undo_log，进行回滚
            if (state == 0) {
                txcService.undo(groupId, unitId);
            }
        } catch (TxcLogicException e) {
            @SuppressWarnings("unchecked")
            List<StatementInfo> statementInfoList = (List<StatementInfo>) e.getAttachment();
            tmReporter.reportTxcUndoException(groupId, unitId, statementInfoList);
            rethrowTxcException = true;
            log.debug("need compensation !");
        }

        try {
            // 清理TXC
            txcService.cleanTxc(groupId, unitId);
        } catch (TxcLogicException e) {
            throw new TransactionClearException(e);
        }

        if (rethrowTxcException) {
            throw TransactionClearException.needCompensation();
        }
    }
```

### TxcServiceImpl
```
    //通过groupid，unitid查询H2保存的影响数据，反转得到补偿sql，然后只想补偿sql
    @Override
    public void undo(String groupId, String unitId) throws TxcLogicException {
        DTXLocalContext.makeUnProxy();
        List<StatementInfo> statementInfoList = new ArrayList<>();
        try {
            List<UndoLogDO> undoLogDOList = txcLogHelper.getUndoLogByGroupAndUnitId(groupId, unitId);

            for (UndoLogDO undoLogDO : undoLogDOList) {
                TableRecordList tableRecords = SqlUtils.blobToObject(undoLogDO.getRollbackInfo(), TableRecordList.class);
                switch (undoLogDO.getSqlType()) {
                    case SqlUtils.SQL_TYPE_UPDATE:
                        tableRecords.getTableRecords().forEach(tableRecord -> statementInfoList.add(UndoLogAnalyser.update(tableRecord)));
                        break;
                    case SqlUtils.SQL_TYPE_DELETE:
                        tableRecords.getTableRecords().forEach(tableRecord -> statementInfoList.add(UndoLogAnalyser.delete(tableRecord)));
                        break;
                    case SqlUtils.SQL_TYPE_INSERT:
                        tableRecords.getTableRecords().forEach(tableRecord -> statementInfoList.add(UndoLogAnalyser.insert(tableRecord)));
                        break;
                    default:
                        break;
                }
            }
            txcSqlExecutor.applyUndoLog(statementInfoList);
        } catch (SQLException e) {
            TxcLogicException exception = new TxcLogicException(e);
            exception.setAttachment(statementInfoList);
            throw exception;
        } finally {
            DTXLocalContext.undoProxyStatus();
        }
    }
```
