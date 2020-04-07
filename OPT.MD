# 待优化

## 1、TM通知TU负载问题
我们知道TM通知TU的时候netty没有做负载均衡，直接选择的第一个
```
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
```

那这里有一个直接的问题是，如果微服务启动了多个，那很大概率会发送到之前TU参与的另外一个，那TU就获取不到Connection，或者在TCC模式下获取不到TccTransactionInfo之类的信息，那回滚或者提交就会失败。目前tx-lcn是通过定时任务完成。这里定时任务会有时间差，另外会带来其他的问题。所以最好通知事务的时候直接通知到原来参与事务的微服务进程。
