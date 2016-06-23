package com.walmart.isd.ips.mw.messaging.test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.pool.PooledConnectionFactory;


/**
 * @author jcdavie
 */
public class SampleJmsConsumer implements Runnable, MessageListener
{
	private final ConnectionFactory connectionFactory;
	private final AtomicBoolean done;
	private final AtomicLong amountProcessed;

	private Connection connection = null;
	private Session session= null;
	private Queue queue;
	private String endpoint = "";
	private MessageConsumer consumer;


	public SampleJmsConsumer(ConnectionFactory cf)
	{
		connectionFactory = cf;
		done = new AtomicBoolean(false);
		amountProcessed = new AtomicLong(0L);
	}


	/*
	 * MessageListener interface implementation
	 */
	@Override
	final public void onMessage(Message message)
	{
		try {
			if (null != message) {
				// Do stuff with message ...
				message.acknowledge();
				incrementAmountProcessed();
				if (message instanceof TextMessage) {
					System.out.println(String.format("%s - %d message(s) processed", 
						((TextMessage)message).getText(), getAmountProcessed()));
				}
			}

		} catch (Exception e) {
			System.out.println(e.getMessage());
			e.printStackTrace();
		}
	}


	/*
	 * Implement the dispose pattern (deterministic cleanup) in the Runnable interface
	 */
	@Override
	public void run()
	{
		try {
			connection = connectionFactory.createConnection();
			session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
			queue = session.createQueue(getEndpoint());
			consumer = session.createConsumer(queue);
			consumer.setMessageListener(this);
			connection.start();

			while (false == isDone()) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException ie) {
					setDone(true);
				}
			}

		} catch (Exception e) {
			System.out.println(e.getMessage());
			e.printStackTrace();
		} finally {
			dispose();
			setDone(true);
		}
	}


	public void dispose()
	{
		if (null != consumer) {
			try {
				consumer.close();
			} catch (JMSException jmse) {
				System.out.println(jmse.getMessage());
			}
		}
		if (null != session) {
			try {
				if (session.getTransacted()) {
					session.commit();
				}
				session.close();
			} catch (JMSException jmse) {
				System.out.println(jmse.getMessage());
			}
		}
		if (null != connection) {
			try {
				connection.close();  // Release the connection back to the pool
			} catch (JMSException jmse) {
				System.out.println(jmse.getMessage());
			}
		}
	}


	final public boolean isDone() {
		return done.get();
	}

	final public void setDone(boolean done) {
		this.done.set(done);
	}

	final public String getEndpoint() {
		return endpoint;
	}

	final public void setEndpoint(String endpoint) {
		this.endpoint = endpoint;
	}

	final public long getAmountProcessed() {
		return amountProcessed.get();
	}
	
	final public long incrementAmountProcessed() {
		return amountProcessed.incrementAndGet();
	}



	/*
	 * For testing only
	 */
	public static void main(String[] args)
	{
		String brokerUrl = "tcp://tstr500241:58407";
		String user = "eim";
		String password = "password";
		String endpoint = "EIM.TEST";

		PooledConnectionFactory pcf = null;

		try {
			ActiveMQConnectionFactory cf = new ActiveMQConnectionFactory(brokerUrl);
			cf.setUserName(user);
			cf.setPassword(password);
			pcf = new PooledConnectionFactory(cf);
			pcf.setMaxConnections(10);
			pcf.setMaximumActiveSessionPerConnection(100);
			pcf.setIdleTimeout(0);
			pcf.start();

			SampleJmsConsumer consumer = new SampleJmsConsumer(pcf);
			consumer.setEndpoint(endpoint);

			Thread t = new Thread(consumer);
			t.start();

			// Wait for consumer thread to end before cleanup up the connection
			while (false == consumer.isDone()) {
				Thread.sleep(5000);
			}

		} catch (Exception e) {
			System.out.println(e.getMessage());
			e.printStackTrace();
		} finally {
			try {
				if (null != pcf) {
					pcf.clear();
					pcf.stop();
				}
			} catch (Exception e) {
				System.out.println("Error stopping connection pool: " + e.getMessage());
				e.printStackTrace();
			}
		}
	}

} // end class