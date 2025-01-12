package ca.bc.gov.open.pssg.rsbc.dps.dpsemailpoller.scheduler;

import ca.bc.gov.dps.monitoring.NotificationService;
import ca.bc.gov.dps.monitoring.SystemNotification;
import ca.bc.gov.open.pssg.rsbc.dps.cache.StorageService;
import ca.bc.gov.open.pssg.rsbc.dps.dpsemailpoller.Keys;
import ca.bc.gov.open.pssg.rsbc.dps.dpsemailpoller.email.DpsEmailException;
import ca.bc.gov.open.pssg.rsbc.dps.dpsemailpoller.email.DpsMSGraphException;
import ca.bc.gov.open.pssg.rsbc.dps.dpsemailpoller.email.configuration.EmailProperties;
import ca.bc.gov.open.pssg.rsbc.dps.dpsemailpoller.email.services.DpsMetadataMapper;
import ca.bc.gov.open.pssg.rsbc.dps.dpsemailpoller.email.services.EmailService;
import ca.bc.gov.open.pssg.rsbc.dps.dpsemailpoller.email.services.DpsMSGraphMetadataMapper;
import ca.bc.gov.open.pssg.rsbc.dps.dpsemailpoller.email.services.MSGraphService;
import ca.bc.gov.open.pssg.rsbc.dps.dpsemailpoller.messaging.MessagingService;
import ca.bc.gov.open.pssg.rsbc.error.DpsError;
import ca.bc.gov.open.pssg.rsbc.error.DpsException;
import ca.bc.gov.open.pssg.rsbc.models.DpsFileInfo;
import ca.bc.gov.open.pssg.rsbc.models.DpsMetadata;
import ca.bc.gov.open.pssg.rsbc.monitoring.MdcConstants;
import com.microsoft.graph.models.Attachment;
import com.microsoft.graph.models.Message;
import microsoft.exchange.webservices.data.core.exception.service.local.ServiceLocalException;
import microsoft.exchange.webservices.data.core.service.item.EmailMessage;
import microsoft.exchange.webservices.data.property.complex.FileAttachment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.event.Level;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.text.MessageFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class EmailPoller {


    public static final String UNDEFINED = "undefined";
    public static final String DPS_BATCH_ID = "dps.batch.id";
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final EmailService emailService;
    private final MSGraphService graphService;
    private final DpsMetadataMapper dpsMetadataMapper;
    private final DpsMSGraphMetadataMapper dpsMSGraphMetadataMapper;
    private final MessagingService messagingService;
    private final String tenant;
    private final StorageService storageService;
    private final EmailProperties emailProperties;

    public EmailPoller(EmailService emailService, MSGraphService graphService, DpsMetadataMapper dpsMetadataMapper,
                       DpsMSGraphMetadataMapper dpsMSGraphMetadataMapper, MessagingService messagingService,
                       StorageService storageService,  @Value("${dps.tenant}") String tenant,
                       EmailProperties emailProperties) {
        this.emailService = emailService;
        this.graphService = graphService;
        this.dpsMetadataMapper = dpsMetadataMapper;
        this.messagingService = messagingService;
        this.tenant = tenant;
        this.storageService = storageService;
        this.dpsMSGraphMetadataMapper = dpsMSGraphMetadataMapper;
        this.emailProperties = emailProperties;
    }

    @Scheduled(cron = "${mailbox.poller.cron}")
    public void pollForEmails() {
        UUID batchId = UUID.randomUUID();
        MDC.put(DPS_BATCH_ID, batchId.toString());
        logger.info("Polling for emails");
        if (emailProperties.isMSGraph()) {
            pollForMSGraphEmails();
        }
        else {
            pollForEwsEmails();
        }
    }

    public void pollForEwsEmails() {
        try {
            List<EmailMessage> dpsEmails = emailService.getDpsInboxEmails();
            logger.info("successfully retrieved {} emails", dpsEmails.size());
            dpsEmails.forEach(item -> {
                logger.info("starting processing email");
                String correlationId = UNDEFINED;
                String filename = UNDEFINED;

                try {
                    logger.debug("attempting to retrieve email attachments");
                    List<FileAttachment> fileAttachments = emailService.getFileAttachments(item.getId().getUniqueId());
                    logger.info("successfully retrieved {} attachments", fileAttachments.size());

                    Optional<FileAttachment> attachment = fileAttachments.stream().findFirst();
                    if (!attachment.isPresent()) throw new DpsEmailException("No attachment present in email.");

                    logger.debug("attempting to store email attachment");
                    String fileId = this.storageService.put(attachment.get().getContent());
                    logger.info("successfully stored attachments {}", attachment.get().getName());

                    logger.debug("attempting to parse email content");
                    DpsMetadata metadata = dpsMetadataMapper.map(
                            item,
                            new DpsFileInfo(fileId, attachment.get().getName(),
                                    attachment.get().getContentType()), this.tenant);
                    correlationId = metadata.getTransactionId().toString();
                    filename = metadata.getFileInfo().getName();
                    MDC.put(MdcConstants.MDC_TRANSACTION_ID_KEY, metadata.getTransactionId().toString());

                    logger.info("successfully parsed  email content");
                    EmailMessage processedItem = emailService.moveToProcessingFolder(item.getId().getUniqueId());
                    metadata.setEmailId(processedItem.getId().getUniqueId());
                    logger.info("successfully moved message to processing folder");

                    messagingService.sendMessage(metadata, this.tenant);
                    logger.info("successfully send message to processing queue");
                    notifySuccess(metadata);
                }
                catch (ServiceLocalException | DpsEmailException | DpsException e ) {
                    logger.error("exception while processing dps emails", e);
                    Optional<EmailMessage> errorHoldEmail = moveToErrorHold(item);
                    if (e instanceof DpsException) {
                        SystemNotification systemNotification = new SystemNotification
                                .Builder()
                                .withLevel(Level.ERROR)
                                .withCorrelationId(correlationId)
                                .withTransactionId(filename)
                                .withAction(buildActionText(errorHoldEmail))
                                .withApplicationName(Keys.APP_NAME)
                                .withComponent(Keys.APP_NAME)
                                .withDetails("test")
                                .withType(((DpsException)e).getDpsError().getCode())
                                .withMessage(e.getMessage())
                                .buildError();

                        NotificationService.notify(systemNotification);
                    }
                }
                finally {
                    MDC.remove(MdcConstants.MDC_TRANSACTION_ID_KEY);
                }

            });
        }
        catch (DpsEmailException e) {
            logger.error("exception while processing dps emails", e);
        }
        finally {
            MDC.remove(DPS_BATCH_ID);
        }
    }

    public void pollForMSGraphEmails() {
        try {
            List<Message> messages = graphService.GetMessages("hasAttachments eq true");
            logger.info("Retrieved {} emails", messages.size());
            for (Message message:  messages)  processMSGraphMessage(message);
        }
        catch (DpsMSGraphException e) {
            logger.error("Exception retrieving emails", e);
        }
        finally {
            MDC.remove(DPS_BATCH_ID);
        }
    }

    private void processMSGraphMessage(Message message)  {
        logger.info("Processing email: {}", message.getId());
        String correlationId = UNDEFINED;
        String filename = UNDEFINED;

        try {
            logger.debug("Retrieving email attachments");
            List<Attachment> attachments = graphService.getAttachments(message);
            logger.info("Retrieved {} attachments", attachments.size());

            if (attachments.isEmpty()) {
                logger.info("Message has no attachments, moving to Error folder");
                graphService.moveToFolder(message.getId(), emailProperties.getErrorFolder(), true);
                return;
            }

            Attachment attachment = attachments.get(0);
            logger.debug("Storing attachment {}", attachment.getName());
            String fileId = this.storageService.put(graphService.getAttachmentContent(attachment));

            logger.debug("Creating metadata content");
            DpsMetadata metadata = dpsMSGraphMetadataMapper.map(message, new DpsFileInfo(fileId, attachment.getName(),
                attachment.getContentType()), this.tenant);
            correlationId = metadata.getTransactionId().toString();
            filename = metadata.getFileInfo().getName();
            MDC.put(MdcConstants.MDC_TRANSACTION_ID_KEY, metadata.getTransactionId().toString());

            logger.info("Moving email {} to processing folder", message.getId());
            Message processedItem = graphService.moveToFolder(message.getId(), emailProperties.getProcessingFolder(), true);
            metadata.setEmailId(processedItem.getId());

            logger.info("Sending email to processing queue");
            messagingService.sendMessage(metadata, this.tenant);
            notifySuccess(metadata);
        }
        catch (DpsMSGraphException | DpsEmailException | DpsException e) {
            logger.error("Processing email caused an exception", e);
            Optional<Message> errorHoldEmail = Optional.of(graphService.moveToFolder(message.getId(), emailProperties.getErrorFolder(), true));

            if (e instanceof DpsException) {
                SystemNotification systemNotification = new SystemNotification
                        .Builder()
                        .withLevel(Level.ERROR)
                        .withCorrelationId(correlationId)
                        .withTransactionId(filename)
                        .withAction(buildActionText(errorHoldEmail))
                        .withApplicationName(Keys.APP_NAME)
                        .withComponent(Keys.APP_NAME)
                        .withDetails("test")
                        .withType(DpsError.EMAIL_PARSING_ERROR.getCode())
                        .withMessage(e.getMessage())
                        .buildError();

                NotificationService.notify(systemNotification);
            }
        }
        finally {
            MDC.remove(MdcConstants.MDC_TRANSACTION_ID_KEY);
        }
    }

    private void notifySuccess(DpsMetadata message) {

        SystemNotification success = new SystemNotification
                .Builder()
                .withTransactionId(message.getTransactionId().toString())
                .withCorrelationId(message.getFileInfo().getName())
                .withApplicationName(Keys.APP_NAME)
                .withComponent("Email Polling")
                .withMessage("Image and metadata successfully extracted from email")
                .withType("DPS EMAIL POLLING SUCCESS")
                .buildSuccess();

        NotificationService.notify(success);

    }

    /**
     * This Job remove junk email from the inbox and move them to the error folder.
     */
    @Scheduled(cron = "${mailbox.poller.cron}")
    public void junkRemoval() {

        if(emailProperties.isMSGraph()) {
            junkMSGraphRemoval();
        } else {
            junkEwsRemoval();
        }
    }
    public void junkEwsRemoval() {
        logger.debug("perform poll for junk emails");
        try {
            List<EmailMessage> junkEmails = emailService.getDpsInboxJunkEmails();
            logger.info("successfully retrieved {} junk emails", junkEmails.size());

            junkEmails.forEach(item -> {

                try {
                    emailService.moveToErrorFolder(item.getId().getUniqueId());
                } catch (ServiceLocalException e) {
                    return;
                }

                logger.info("successfully moved message to errorHold folder");
            });

        } catch (DpsEmailException e) {
            logger.error("exception while cleaning junk emails", e);
        }
    }

    public void junkMSGraphRemoval() {
        logger.debug("Polling for junk emails");
        try {
            List<Message> messages = graphService.GetMessages("hasAttachments ne true");
            logger.info("successfully retrieved {} junk emails", messages.size());

            for (Message message:  messages) {
                try {
                    graphService.moveToFolder(message.getId(), emailProperties.getErrorFolder(), true);
                }
                catch (Exception e) {
                    logger.error("Exception moving junk email to errorHold folder.", e);
                }
            }
        } catch (DpsEmailException e) {
            logger.error("Exception while retrieving junk emails", e);
        }
    }


    private String buildActionText(Optional<?> item) {
        try {
            if (item.isPresent()) {
                return MessageFormat.format("Manual intervention, email can be found in errorHold folder of {0}", (item.get() instanceof Message) ? ((Message)item.get()).getToRecipients() : ((EmailMessage)item.get()).getToRecipients());
            } else {
                return "Email message not moved to errorHold folder";
            }
        } catch (ServiceLocalException e) {
            return "Email message not moved to errorHold folder";
        } catch (Exception e) {
            return "Email message not moved to errorHold folder";
        }
    }



    private Optional<EmailMessage> moveToErrorHold(EmailMessage item) {
        try {
            return Optional.of(emailService.moveToErrorFolder(item.getId().getUniqueId()));
        }
        catch (ServiceLocalException e) {
            return Optional.empty();
        }

    }

}
