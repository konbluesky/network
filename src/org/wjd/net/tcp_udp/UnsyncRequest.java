package org.wjd.net.tcp_udp;

import java.nio.ByteBuffer;

public class UnsyncRequest
{

	/**
	 * 请求携带的消息
	 */
	private BaseMessage message;

	/**
	 * 取消标记
	 */
	private boolean cancelled;

	/**
	 * 网络超时时间
	 */
	private int timeout = 10;

	/**
	 * 网络连接时间
	 */
	private int connectTime;

	/**
	 * 服务器响应回调接口
	 */
	private ResponseHandler responseHandler;

	/**
	 * 网络异常回调接口
	 */
	private NetErrorHandler neterrorHandler;

	/**
	 * 请求目的地的IP
	 */
	private String host;

	/**
	 * 请求目的地端口
	 */
	private int port;

	/**
	 * 消息是否需要等待响应
	 */
	private boolean waitResponse = true;

	/**
	 * udp请求使用此构造方法
	 * 
	 * @param responseHandler
	 * @param neterrorHandler
	 * @param host
	 * @param port
	 */
	public UnsyncRequest(ResponseHandler responseHandler,
			NetErrorHandler neterrorHandler, String host, int port)
	{
		this.responseHandler = responseHandler;
		this.neterrorHandler = neterrorHandler;
		this.host = host;
		this.port = port;
	}

	/**
	 * tcp请求使用此构造方法
	 * 
	 * @param responseHandler
	 * @param neterrorHandler
	 */
	public UnsyncRequest(ResponseHandler responseHandler,
			NetErrorHandler neterrorHandler)
	{
		this.responseHandler = responseHandler;
		this.neterrorHandler = neterrorHandler;
	}

	/**
	 * 设置超时时间
	 * 
	 * @param time
	 */
	public void setConnectTimeout(int time)
	{
		this.timeout = time;
	}

	/**
	 * 每个一秒计算请求是否超时
	 * 
	 * @return
	 */
	protected boolean connectTimeout()
	{
		connectTime++;
		if (connectTime > timeout)
		{
			return true;
		}
		return false;
	}

	/**
	 * 取消请求
	 */
	public synchronized void cancel()
	{
		cancelled = true;
		responseHandler = null;
		neterrorHandler = null;
	}

	/**
	 * 返回取消标记
	 * 
	 * @return
	 */
	protected synchronized boolean isCancelled()
	{
		return cancelled;
	}

	/**
	 * 处理响应
	 * 
	 * @return
	 */
	public void handleResponse()
	{
		if (null != responseHandler)
		{
			responseHandler.handleResponse(message);
		}
	}

	/**
	 * 处理网络异常
	 * 
	 * @return
	 */
	public void handleNetError()
	{
		if (null != neterrorHandler)
		{
			neterrorHandler.handleNetError(message);
		}
	}

	// /**
	// * 服务器返回的数据通过此方法获取
	// *
	// * @return
	// */
	// public byte[] getData()
	// {
	// return data;
	// }

	// /**
	// * 获取数据长度
	// *
	// * @return
	// */
	// public int getReceivedDataLength()
	// {
	// return null == data ? 0 : data.length;
	// }

	// /**
	// * 设置请求的数据(非http请求)
	// *
	// * 数据前10个字节设置规则为：
	// *
	// * 1. 前两个字节为数据长度
	// *
	// * 2. 第3~10个字节为时间戳
	// *
	// * 3. 响应数据也要遵守此规则，长度可以用来组包，时间戳(和请求的时间戳相同)用来匹配请求。
	// *
	// * @param data
	// * 业务数据
	// * @param len
	// * 数据包长度 (不包括长度本身的两个字节， len = data.length + sizeof(timestamp))
	// * @param timestamp
	// * 请求时间戳
	// */
	// public void setSendData(byte[] data)
	// {
	// this.timestamp = System.currentTimeMillis();
	// this.data = new byte[data.length + HEAD_LEN];
	// ByteBuffer buffer = ByteBuffer.wrap(this.data);
	// buffer.putShort((short) (data.length - 2));
	// buffer.putLong(this.timestamp);
	// buffer.put(data);
	// }

	// /**
	// * 设置接收到的数据
	// *
	// * @param data
	// */
	// public void setReceivedData(byte[] data)
	// {
	// this.data = data;
	// }

	/**
	 * 获取udp请求域名
	 * 
	 * @return
	 */
	public String getHost()
	{
		return host;
	}

	/**
	 * 获取udp请求端口
	 * 
	 * @return
	 */
	public int getPort()
	{
		return port;
	}

	// /**
	// * 设置时间戳
	// *
	// * @param timestamp
	// */
	// protected void setTimestamp(long timestamp)
	// {
	// this.timestamp = timestamp;
	// }
	//
	// /**
	// * 获取时间戳
	// *
	// * @return
	// */
	// protected long getTimestamp()
	// {
	// return timestamp;
	// }

	public void setMessage(BaseMessage message)
	{
		this.message = message;
	}

	public BaseMessage getMessage()
	{
		return message;
	}

	public byte[] createSendData()
	{
		byte[] busiData = message.createData();
		byte[] data = new byte[busiData.length + 2];
		ByteBuffer wrapper = ByteBuffer.wrap(data);
		wrapper.putShort((short) busiData.length);
		wrapper.put(busiData);
		return data;
	}

	public boolean isWaitResponse()
	{
		return waitResponse;
	}

	public void setWaitResponse(boolean waitResponse)
	{
		this.waitResponse = waitResponse;
	}
}
