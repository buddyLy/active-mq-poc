package com.walmart.isd.ips.mw.messaging.test;

import java.util.concurrent.atomic.AtomicBoolean;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.pool.PooledConnectionFactory;


/**
 * @author jcdavie
 */
public class SampleJmsProducer implements Runnable
{
	private final ConnectionFactory connectionFactory;
	private final AtomicBoolean done;
	
	private Connection connection = null;
	private Session session = null;
	private Queue queue = null;
	private String endpoint = "";
	private MessageProducer producer = null;
	private TextMessage message = null;
	
	private int numberOfMessages = 1;
	private int messageInterval = 2;  // seconds


	public SampleJmsProducer(ConnectionFactory cf)
	{
		connectionFactory = cf;
		done = new AtomicBoolean(false);
	}


	/*
	 * Implement the dispose pattern (deterministic cleanup) in the Runnable interface
	 */
	@Override
	public void run()
	{
		try {
			connection = connectionFactory.createConnection();
			connection.start();
			session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
			queue = session.createQueue(endpoint);
			producer = session.createProducer(queue);
			message = session.createTextMessage("");
			
			for (int i = 0; i < numberOfMessages ; i++) {
				message.clearBody();
				message.clearProperties();
				message.setStringProperty("message", "" + i);  // How to set a header value
				message.setText("Test message " + i);
				
				System.out.println("Producer sending msg:" + message.getText());
				producer.send(message);
				try {
					Thread.sleep(messageInterval * 1000);
				} catch (InterruptedException ie) {
					break;
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
		if (null != producer) {
			try {
				producer.close();
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

	public int getNumberOfMessages() {
		return numberOfMessages;
	}

	public void setNumberOfMessages(int numberOfMessages) {
		this.numberOfMessages = numberOfMessages;
	}

	public int getMessageInterval() {
		return messageInterval;
	}

	public void setMessageInterval(int messageInterval) {
		this.messageInterval = messageInterval;
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
			pcf.setMaximumActiveSessionPerConnection(10);
			pcf.setIdleTimeout(0);
			pcf.start();

			SampleJmsProducer producer = new SampleJmsProducer(pcf);
			producer.setEndpoint(endpoint);
			producer.setNumberOfMessages(5);	// Number of messages to send
			producer.setMessageInterval(5);		// 5 second message interval
			
			Thread t = new Thread(producer);
			t.start();
			
			// Wait for producer thread to end before cleanup up the connection
			while (false == producer.isDone()) {
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
