package rocks.inspectit.server.mail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.mail.AuthenticationFailedException;
import javax.mail.MessagingException;
import javax.mail.NoSuchProviderException;
import javax.mail.Session;
import javax.mail.Transport;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.mail.DefaultAuthenticator;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.HtmlEmail;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import rocks.inspectit.server.externalservice.IExternalService;
import rocks.inspectit.shared.all.cmr.property.spring.PropertyUpdate;
import rocks.inspectit.shared.all.externalservice.ExternalServiceStatus;
import rocks.inspectit.shared.all.externalservice.ExternalServiceType;
import rocks.inspectit.shared.all.spring.logger.Log;
import rocks.inspectit.shared.all.util.EMailUtils;

/**
 * Central component for sending e-mails.
 *
 * @author Alexander Wert
 * @author Marius Oehler
 *
 */
@Component
public class EMailSender implements IExternalService {

	/**
	 * The delay in seconds between consecutive automatic connection checks.
	 */
	private static final Long[] EXECUTION_DELAYS = { 15L, 30L, 60L, 120L };

	/**
	 * Logger for the class.
	 */
	@Log
	Logger log;

	/**
	 * SMTP Server enabled.
	 */
	@Value("${mail.enable}")
	boolean smtpEnabled;

	/**
	 * SMTP Server host.
	 */
	@Value("${mail.smtp.host}")
	String smtpHost;

	/**
	 * SMTP Server port.
	 */
	@Value("${mail.smtp.port}")
	int smtpPort;

	/**
	 * SMTP user name.
	 */
	@Value("${mail.smtp.user}")
	String smtpUser;

	/**
	 * Password for SMTP authentication.
	 */
	@Value("${mail.smtp.passwd}")
	String smtpPassword;

	/**
	 * The e-mail address used as sender.
	 */
	@Value("${mail.from}")
	String senderAddress;

	/**
	 * Displayed name of the sender.
	 */
	@Value("${mail.from.name}")
	String senderName;

	/**
	 * A comma separated list of default recipient e-mail addresses.
	 */
	@Value("${mail.default.to}")
	String defaultRecipientString;

	/**
	 * Additional SMTP properties as a comma separated string.
	 */
	@Value("${mail.smtp.properties}")
	String smtpPropertiesString;

	/**
	 * Whether a test mail should be send after SMTP property update.
	 */
	@Value("${mail.test.enabled}")
	boolean testMailEnabled;

	/**
	 * The recipient address for the test e-mail.
	 */
	@Value("${mail.test.recipient}")
	String testMailRecipient;

	/**
	 * Unwrapped list of default recipients.
	 */
	private List<String> defaultRecipients = new ArrayList<>();

	/**
	 * SMTP connection state.
	 */
	private boolean connected = false;

	/**
	 * Additional SMTP properties that might be required for certain SMTP servers.
	 */
	private Properties additionalProperties = new Properties();

	/**
	 * The {@link ObjectFactory} used to created object instances.
	 */
	private ObjectFactory objectFactory = new ObjectFactory();

	/**
	 * {@link ExecutorService} instance.
	 */
	@Autowired
	@Resource(name = "scheduledExecutorService")
	private ScheduledExecutorService scheduledExecutorService;

	/**
	 * Instance of {@link ConnectionCheck}.
	 */
	private final ConnectionCheck connectionCheck = new ConnectionCheck();

	/**
	 * The future of the executed {@link ConnectionCheck}.
	 */
	private Future<?> connectionCheckFuture;

	/**
	 * The index of the next used execution delay.
	 */
	private int executionDelayIndex = 0;

	/**
	 * {@inheritDoc}
	 */
	public boolean sendEMail(String subject, String htmlMessage, String textMessage, List<String> recipients) {
		if (StringUtils.isEmpty(subject)) {
			throw new IllegalArgumentException("The given subject may not be null or empty.");
		}
		if (StringUtils.isEmpty(htmlMessage)) {
			throw new IllegalArgumentException("The given HTML body may not be null or empty.");
		}
		if (StringUtils.isEmpty(textMessage)) {
			throw new IllegalArgumentException("The given text body may not be null or empty.");
		}
		if (!connected) {
			log.warn("Failed sending e-mail! E-Mail service cannot connect to the SMTP server. Check the connection settings!");
			return false;
		}
		try {
			HtmlEmail email = prepareHtmlEmail(recipients);
			email.setSubject(subject);
			email.setHtmlMsg(htmlMessage);
			email.setTextMsg(textMessage);
			email.send();
			return true;
		} catch (EmailException | IllegalArgumentException e) {
			if (log.isWarnEnabled()) {
				log.warn("Failed sending e-mail!", e);
			}
			return false;
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean isConnected() {
		return getServiceStatus() == ExternalServiceStatus.CONNECTED;
	}

	/**
	 * Unwrap the comma separated list string of default recipients into a real list.
	 */
	@PropertyUpdate(properties = { "mail.default.to" })
	private void parseRecipientsString() {
		defaultRecipients.clear();
		if (null != defaultRecipientString) {
			String[] strArray = defaultRecipientString.split(",");
			for (String element : strArray) {
				String address = element.trim();
				if (EMailUtils.isValidEmailAddress(address)) {
					defaultRecipients.add(address);
				}
			}
		}

	}

	/**
	 * Unwrap the comma separated list string of additional properties into real properties object.
	 */
	private void parseAdditionalPropertiesString() {
		additionalProperties.clear();
		if (null != smtpPropertiesString) {
			String[] strArray = smtpPropertiesString.split(",");
			for (String property : strArray) {
				int equalsIndex = property.indexOf('=');
				if ((equalsIndex > 0) && (equalsIndex < (property.length() - 1))) {
					additionalProperties.put(property.substring(0, equalsIndex).trim(), property.substring(equalsIndex + 1).trim());
				}
			}
		}
	}

	/**
	 * Parses the SMTP properties and checks whether a connection can be established.
	 */
	@PropertyUpdate(properties = { "mail.smtp.host", "mail.smtp.port", "mail.smtp.user", "mail.smtp.passwd", "mail.smtp.properties", "mail.test.enabled" })
	public void onSmtpPropertiesChanged() {
		if (!smtpEnabled) {
			return;
		}
		onSmtpPropertiesChanged(true);
	}

	/**
	 * Parses the SMTP properties and checks whether a connection can be established.
	 *
	 * @param sendTestMailIfEnabled
	 *            send a test email if this feature is enabled
	 */
	private void onSmtpPropertiesChanged(boolean sendTestMailIfEnabled) {
		parseAdditionalPropertiesString();
		checkConnection(sendTestMailIfEnabled && testMailEnabled);
	}

	/**
	 * Initialize E-Mail service.
	 */
	@PropertyUpdate(properties = { "mail.enable" })
	public void init() {
		init(true);
	}

	/**
	 * Method which is executed after bean is created.
	 */
	@PostConstruct
	public void postConstruct() {
		init(false);
	}

	/**
	 * Initialize E-Mail service.
	 *
	 * @param sendTestMailIfEnabled
	 *            send a test email if this feature is enabled
	 */
	private void init(boolean sendTestMailIfEnabled) {
		if (!smtpEnabled) {
			return;
		} else if (log.isInfoEnabled()) {
			log.info("|-eMail Service initialized");
		}

		parseRecipientsString();
		onSmtpPropertiesChanged(sendTestMailIfEnabled);
	}

	/**
	 * Checks connection to SMTP server.
	 *
	 * @param sendTestMail
	 *            specifies whether a test email should be send after the connection is established
	 */
	private void checkConnection(boolean sendTestMail) {
		if ((connectionCheckFuture != null) && !connectionCheckFuture.isDone()) {
			connectionCheckFuture.cancel(true);
		}
		connectionCheck.sendTestEmail = sendTestMail;
		connectionCheckFuture = scheduledExecutorService.submit(connectionCheck);
	}

	/**
	 * Prepares an email object including the default recipients.
	 *
	 * @param recipients
	 *            recipient to send to.
	 * @return Returns a prepared {@link HtmlEmail} object.
	 * @throws EmailException
	 *             is thrown when the from address could not be set
	 */
	private HtmlEmail prepareHtmlEmail(List<String> recipients) throws EmailException {
		return prepareHtmlEmail(recipients, true);
	}

	/**
	 * Prepares an email object.
	 *
	 * @param recipients
	 *            recipient to send to.
	 * @param includeDefaultRecipients
	 *            whether the default recipients should be added to the mail
	 * @return Returns a prepared {@link HtmlEmail} object.
	 * @throws EmailException
	 *             is thrown when the from address could not be set
	 */
	private HtmlEmail prepareHtmlEmail(List<String> recipients, boolean includeDefaultRecipients) throws EmailException {
		HtmlEmail email = objectFactory.createHtmlEmail();
		email.setHostName(smtpHost);
		email.setSmtpPort(smtpPort);
		email.setAuthentication(smtpUser, smtpPassword);
		email.setFrom(senderAddress, senderName);

		if ((additionalProperties != null) && !additionalProperties.isEmpty()) {
			email.getMailSession().getProperties().putAll(additionalProperties);
		}

		if (includeDefaultRecipients) {
			for (String defaultTo : defaultRecipients) {
				try {
					email.addTo(defaultTo);
				} catch (EmailException e) {
					if (log.isWarnEnabled()) {
						log.warn("Invalid recipient e-mail address!", e);
					}
				}
			}
		}
		if (recipients != null) {
			for (String to : recipients) {
				try {
					email.addTo(to);
				} catch (EmailException e) {
					if (log.isWarnEnabled()) {
						log.warn("Invalid recipient e-mail address!", e);
					}
				}
			}
		}

		return email;
	}

	/**
	 * * Sending a test e-mail to the configured recipient if a valid SMTP server has been
	 * configured and connected.
	 */
	@PropertyUpdate(properties = { "mail.test.recipient" })
	private void sendTestMail() {
		if (getServiceStatus() == ExternalServiceStatus.DISABLED) {
			return;
		} else if (!isConnected()) {
			if (log.isInfoEnabled()) {
				log.info("Cannot send a test e-mail because the SMTP connection is not established.");
			}
			return;
		} else if (StringUtils.isEmpty(testMailRecipient)) {
			if (log.isInfoEnabled()) {
				log.info("A recipient address has to be specified in order to send a test e-mail.");
			}
			return;
		} else if (!EMailUtils.isValidEmailAddress(testMailRecipient)) {
			if (log.isWarnEnabled()) {
				log.warn("The specified recipient address for the test e-mail is not valid.");
			}
			return;
		}

		if (log.isDebugEnabled()) {
			log.debug("Sending test e-mail to '{}'.", testMailRecipient);
		}

		try {
			HtmlEmail htmlEmail = prepareHtmlEmail(Collections.singletonList(testMailRecipient), false);
			htmlEmail.setSubject("inspectIT Test E-Mail");
			htmlEmail.setTextMsg("Hello, this is a test e-mail. You have successfully configured the SMTP server used by inspectIT!");
			htmlEmail.send();

			if (log.isInfoEnabled()) {
				log.info("Successfully sent test e-mail to '{}'.", testMailRecipient);
			}
		} catch (EmailException | IllegalArgumentException e) {
			if (log.isWarnEnabled()) {
				log.warn("Failed sending test e-mail!", e);
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ExternalServiceType getServiceType() {
		return ExternalServiceType.MAIL_SENDER;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ExternalServiceStatus getServiceStatus() {
		if (!smtpEnabled) {
			return ExternalServiceStatus.DISABLED;
		}

		if (connected) {
			return ExternalServiceStatus.CONNECTED;
		} else {
			return ExternalServiceStatus.DISCONNECTED;
		}
	}

	/**
	 * Factory class to create objects required by the EMailSender. This class primary exists for
	 * better testing process.
	 *
	 * @author Marius Oehler
	 *
	 */
	class ObjectFactory {
		/**
		 * Get a {@link Transport} object for a SMTP connection.
		 *
		 * @return A new {@link Transport}.
		 * @throws NoSuchProviderException
		 *             If provider for SMTP protocol is not found.
		 */
		public Transport getSmtpTransport() throws NoSuchProviderException {
			return Session.getInstance(additionalProperties, new DefaultAuthenticator(smtpUser, smtpPassword)).getTransport("smtp");
		}

		/**
		 * Creates a new instance of {@link HtmlEmail}.
		 *
		 * @return the created instance
		 */
		public HtmlEmail createHtmlEmail() {
			return new HtmlEmail();
		}
	}

	/**
	 * Runnable to execute the connection check.
	 *
	 * @author Marius Oehler
	 *
	 */
	private class ConnectionCheck implements Runnable {

		/**
		 * Specifies whether a test email should be send if the connection check was successful.
		 */
		private boolean sendTestEmail = false;

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void run() {
			if (log.isDebugEnabled()) {
				log.debug("Check connection to SMTP server..");
			}

			try {
				Transport transport = objectFactory.getSmtpTransport();
				transport.connect(smtpHost, smtpPort, smtpUser, smtpPassword);
				transport.close();
				if (!connected) {
					if (log.isInfoEnabled()) {
						log.info("|-eMail Service connected.");
					}
				}
				connected = true;
			} catch (AuthenticationFailedException e) {
				if (connected) {
					if (log.isInfoEnabled()) {
						log.info("|-eMail Service was not able to connect! Authentication failed! Reason: {}", e.getMessage());
					}
				}
				connected = false;
			} catch (MessagingException e) {
				if (connected) {
					if (log.isInfoEnabled()) {
						log.info("|-eMail Service was not able to connect! Reason: {}", e.getMessage());
					}
				}
				connected = false;
			} catch (RuntimeException e) {
				// this catch ensures that this runnable is not crashing
				if (log.isWarnEnabled()) {
					log.warn("An unexpected exception has been thrown during availability check.", e);
				}
			}

			if (smtpEnabled) {
				if (connected) {
					executionDelayIndex = 0;
				}

				scheduledExecutorService.schedule(this, EXECUTION_DELAYS[executionDelayIndex], TimeUnit.SECONDS);

				if (!connected) {
					if (executionDelayIndex < (EXECUTION_DELAYS.length - 1)) {
						executionDelayIndex++;
					}
				} else if (sendTestEmail) {
					sendTestEmail = false;
					sendTestMail();
				}
			}
		}

	}
}
