package org.wjd.net.tcp_udp;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

import android.os.Handler;

public abstract class BaseChannel extends Handler
{

	/**
	 * 待发送的消息队列
	 */
	List<UnsyncRequest> messageToSend = new LinkedList<UnsyncRequest>();

	/**
	 * 已发送的消息队列
	 */
	List<UnsyncRequest> messageSended = new LinkedList<UnsyncRequest>();

	/**
	 * 推送消息回调接口
	 */
	private PushHandler pushHandler;

	/**
	 * 发送线程
	 */
	private Sender senderThread = null;

	/**
	 * 计时线程
	 */
	private Timer timerThread = null;

	/**
	 * 监听线程，负责消息的接收，连接管理等
	 */
	private Listener listenerThread = null;

	private boolean threadInited = false;

	/**
	 * 初始化:初始化线程，初始化本地链接
	 */
	protected synchronized void initThread()
	{
		if (threadInited)
		{
			return;
		}
		senderThread = new Sender("Message Sender ...");
		senderThread.start();
		timerThread = new Timer("Message Timer ...");
		timerThread.start();
		listenerThread = new Listener("Message Listener ...");
		listenerThread.start();
		threadInited = true;
	}

	/**
	 * 推送消息的处理接口设置
	 * 
	 * @param pushHandler
	 */
	protected void setPushHandler(PushHandler pushHandler)
	{
		this.pushHandler = pushHandler;
	}

	/**
	 * 初始化本地链接
	 */
	protected abstract boolean initLocalConnection(String ip, int port);

	/**
	 * 释放:释放线程，释放本地链接
	 */
	protected void unInit()
	{
		unInitThread();
		clearCache();
		unInitLocalConnection();
	}

	/**
	 * 清除缓存队列
	 */
	private void clearCache()
	{
		if (!messageToSend.isEmpty())
		{
			obtainMessage(NETERROR_HANDLE,
					messageToSend.get(messageToSend.size() - 1)).sendToTarget();
		} else if (!messageSended.isEmpty())
		{
			obtainMessage(NETERROR_HANDLE,
					messageSended.get(messageSended.size() - 1)).sendToTarget();
		}
		messageToSend.clear();
		messageSended.clear();
	}

	/**
	 * 停止线程
	 */
	private synchronized void unInitThread()
	{
		threadInited = false;
		if (null != senderThread)
		{
			senderThread.stopThread();
			senderThread = null;
		}
		if (null != timerThread)
		{
			timerThread.stopThread();
			timerThread = null;
		}
		if (null != listenerThread)
		{
			listenerThread.stopThread();
			listenerThread = null;
		}
	}

	/**
	 * 释放本地链接
	 */
	protected abstract void unInitLocalConnection();

	/**
	 * 将消息发送到待发送队列
	 * 
	 * @param message
	 */
	protected void storeMessageToSend(UnsyncRequest message)
	{
		synchronized (messageToSend)
		{
			messageToSend.add(message);
			if (messageToSend.size() == 1)
			{
				messageToSend.notify();
			}
		}
	}

	/**
	 * 消息发送线程
	 * 
	 * @author wjd
	 * 
	 */
	private class Sender extends Thread
	{

		public Sender(String threadName)
		{
			super(threadName);
		}

		private boolean running = true;

		public void stopThread()
		{
			this.interrupt();
			running = false;
		}

		@Override
		public void run()
		{
			while (running)
			{
				try
				{
					Thread.sleep(3);
				} catch (InterruptedException e1)
				{
					e1.printStackTrace();
				}
				UnsyncRequest message = null;
				synchronized (messageToSend)
				{
					if (!messageToSend.isEmpty())
					{
						message = messageToSend.remove(0);
					}
					if (null == message)
					{
						try
						{
							messageToSend.wait();
						} catch (InterruptedException e)
						{
							e.printStackTrace();
						}
					} else
					{
						try
						{
							// 延迟一秒发送请求，给用户一定的时间来取消请求
							Thread.sleep(1000);
						} catch (InterruptedException e)
						{
							e.printStackTrace();
						}
						doSend(message);
					}
				}
			}
		}

		/**
		 * 发送
		 * 
		 * @param request
		 */
		private void doSend(UnsyncRequest request)
		{
			if (request.isCancelled())
			{
				return;
			}
			if (!doSendImpl(request))
			{
				return;
			}
			// 将消息加入已发送队列
			if (request.isWaitResponse())
			{
				synchronized (messageSended)
				{
					messageSended.add(request);
					if (messageSended.size() == 1)
					{
						messageSended.notify();
					}
				}
			}
		}
	}

	/**
	 * 消息发送实现
	 * 
	 * @param message
	 */
	protected abstract boolean doSendImpl(UnsyncRequest message);

	/**
	 * 立刻发送消息，消息不经过缓存，直接发送
	 * 
	 * ps：只有UDP支持此功能
	 * 
	 * @param req
	 * @param addr
	 * @return
	 */
	protected abstract void doSendImmediately(UnsyncRequest req,
			InetAddress addr);

	/**
	 * 计时器线程
	 * 
	 * @author wjd
	 * 
	 */
	private class Timer extends Thread
	{

		private boolean running = true;

		public Timer(String name)
		{
			super(name);
		}

		public void stopThread()
		{
			this.running = false;
			this.interrupt();
		}

		@Override
		public void run()
		{
			while (running)
			{
				try
				{
					// 防止线程占据messageSended锁不释放
					Thread.sleep(3);
				} catch (InterruptedException e)
				{
					e.printStackTrace();
				}
				synchronized (messageSended)
				{
					if (messageSended.isEmpty())
					{
						try
						{
							messageSended.wait();
						} catch (InterruptedException e)
						{
							e.printStackTrace();
						}
					} else
					{
						try
						{
							Thread.sleep(995);
						} catch (InterruptedException e)
						{
							e.printStackTrace();
						}
						doTime();
					}
				}
			}
		}

		/**
		 * 计时
		 */
		private void doTime()
		{
			for (int i = messageSended.size() - 1, n = 0; i >= n; --i)
			{
				if (messageSended.get(i).connectTimeout())
				{
					// 将网络回调处理发送到主线程,防止计时线程阻塞
					obtainMessage(NETERROR_HANDLE, messageSended.remove(i))
							.sendToTarget();
				}
			}
		}
	}

	/**
	 * 消息监听线程
	 * 
	 * @author wjd
	 * 
	 */
	private class Listener extends Thread
	{

		private boolean running = true;

		public Listener(String name)
		{
			super(name);
		}

		public void stopThread()
		{
			running = false;
			this.interrupt();
		}

		@Override
		public void run()
		{
			while (running)
			{
				try
				{
					Thread.sleep(3l);
				} catch (InterruptedException e)
				{
					e.printStackTrace();
				}

				running = doLinsenImpl();
			}
		}
	}

	/**
	 * 解析接收到的数据
	 * 
	 * @param src
	 */
	protected void parseMessage(byte[] src)
	{
		if (null == src)
		{
			return;
		}
		ByteBuffer wrapper = ByteBuffer.wrap(src);
		int len = wrapper.getShort();
		if (len + 2 != src.length)
		{
			return;
		}
		byte[] busiData = new byte[len];
		wrapper.get(busiData);
		UnsyncRequest request = responseMatch(busiData);
		if (null != request)
		{
			if (!request.isCancelled())
			{
				request.getMessage().parseData(busiData);
				obtainMessage(RESPONSE_HANDLE, request).sendToTarget();
			}
		} else
		{
			if (null != pushHandler)
			{
				pushHandler.handlePush(busiData);
			}
		}
	}

	private UnsyncRequest responseMatch(byte[] busiData)
	{
		synchronized (messageSended)
		{
			for (int i = 0, n = messageSended.size(); i < n; ++i)
			{
				ByteBuffer wrapper = ByteBuffer.wrap(busiData);
				if (messageSended.get(i).getMessage().match(wrapper))
				{
					return messageSended.remove(i);
				}
			}
		}
		return null;
	}

	/**
	 * 消息监听实现
	 */
	protected abstract boolean doLinsenImpl();

	protected static final int RESPONSE_HANDLE = 1;

	protected static final int NETERROR_HANDLE = 2;

	@Override
	public void handleMessage(android.os.Message msg)
	{
		switch (msg.what)
		{
			case RESPONSE_HANDLE:
				((UnsyncRequest) msg.obj).handleResponse();
				break;
			case NETERROR_HANDLE:
				((UnsyncRequest) msg.obj).handleNetError();
				break;
			default:
				break;
		}
	}
}
