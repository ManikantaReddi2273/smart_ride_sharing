package com.ridesharing.rideservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import jakarta.annotation.PostConstruct;

/**
 * Email Service responsible for sending booking confirmation emails.
 */
@Service
@Slf4j
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String defaultFromAddress;

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy");
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("h:mm a");

    /**
     * Verify email configuration on startup
     */
    @PostConstruct
    public void verifyEmailConfig() {
        try {
            log.info("📧 Email Service Configuration:");
            log.info("  Host: {}", ((org.springframework.mail.javamail.JavaMailSenderImpl) mailSender).getHost());
            log.info("  Port: {}", ((org.springframework.mail.javamail.JavaMailSenderImpl) mailSender).getPort());
            log.info("  Username: {}", defaultFromAddress);
            log.info("  SMTP Auth: {}", ((org.springframework.mail.javamail.JavaMailSenderImpl) mailSender)
                .getJavaMailProperties().get("mail.smtp.auth"));
            log.info("  STARTTLS: {}", ((org.springframework.mail.javamail.JavaMailSenderImpl) mailSender)
                .getJavaMailProperties().get("mail.smtp.starttls.enable"));
        } catch (Exception e) {
            log.warn("Could not log email configuration: {}", e.getMessage());
        }
    }

    /**
     * Send booking confirmation email to passenger
     *
     * @param passengerEmail Passenger's email address
     * @param passengerName Passenger's name
     * @param driverName Driver's name
     * @param driverEmail Driver's email address
     * @param rideDetails Ride details (source, destination, date, time, vehicle, etc.)
     * @param seatsBooked Number of seats booked
     */
    @Async
    public void sendBookingConfirmationToPassenger(
            String passengerEmail,
            String passengerName,
            String driverName,
            String driverEmail,
            Map<String, Object> rideDetails,
            Integer seatsBooked) {

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message,
                    MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                    StandardCharsets.UTF_8.name());

            helper.setFrom(Optional.ofNullable(defaultFromAddress).orElse("no-reply@smartridesharing.com"));
            helper.setTo(passengerEmail);
            helper.setSubject("Smart Ride Sharing - Booking Confirmed");
            helper.setText(buildPassengerEmailBody(passengerName, driverName, driverEmail, rideDetails, seatsBooked), true);

            log.info("Attempting to send booking confirmation email to passenger: {}", passengerEmail);
            mailSender.send(message);
            log.info("Booking confirmation email sent successfully to passenger: {}", passengerEmail);
        } catch (Exception ex) {
            log.error("Failed to send booking confirmation email to passenger {}: {}", passengerEmail, ex.getMessage(), ex);
        }
    }

    private String buildPassengerEmailBody(String passengerName,
                                           String driverName,
                                           String driverEmail,
                                           Map<String, Object> rideDetails,
                                           Integer seatsBooked) {
        return EmailTemplate.builder()
                .title("Your ride is booked!")
                .greeting(String.format("Hi %s,", safeValue(passengerName)))
                .intro("You've successfully reserved a seat. You'll find all the key details below.")
                .addSection("Ride Summary", buildRideSummary(rideDetails, seatsBooked, true))
                .addSection("Driver Contact", buildDriverSummary(driverName, driverEmail))
                .footer(defaultFooter())
                .build();
    }

    private String buildDriverSummary(String driverName, String driverEmail) {
        return """
                <ul>
                    <li><strong>Name:</strong> %s</li>
                    <li><strong>Email:</strong> %s</li>
                </ul>
                """.formatted(
                safeValue(driverName),
                safeValue(driverEmail)
        );
    }

    private String buildRideSummary(Map<String, Object> rideDetails, Integer seatsBooked, boolean includeVehicle) {
        String source = safeValue(rideDetails.get("source"));
        String destination = safeValue(rideDetails.get("destination"));
        String rideDate = formatDate(rideDetails.get("rideDate"));
        String rideTime = formatTime(rideDetails.get("rideTime"));
        String vehicleModel = safeValue(rideDetails.get("vehicleModel"));
        String vehicleLicensePlate = safeValue(rideDetails.get("vehicleLicensePlate"));

        StringBuilder builder = new StringBuilder();
        builder.append("<ul>");
        builder.append("<li><strong>Route:</strong> ").append(source).append(" → ").append(destination).append("</li>");
        builder.append("<li><strong>Date:</strong> ").append(rideDate).append("</li>");
        builder.append("<li><strong>Time:</strong> ").append(rideTime).append("</li>");
        builder.append("<li><strong>Seats Booked:</strong> ").append(seatsBooked != null ? seatsBooked : "N/A").append("</li>");
        if (includeVehicle) {
            builder.append("<li><strong>Vehicle:</strong> ").append(vehicleModel);
            if (!vehicleLicensePlate.equals("N/A")) {
                builder.append(" (").append(vehicleLicensePlate).append(")");
            }
            builder.append("</li>");
        }
        builder.append("</ul>");
        return builder.toString();
    }

    private String defaultFooter() {
        return """
                Need to make changes? Coordinate directly with the other rider.
                <br/><br/>
                Safe travels! <br/>Smart Ride Sharing Team
                """;
    }

    private String safeValue(Object value) {
        if (value == null) {
            return "N/A";
        }
        String asString = value.toString().trim();
        return asString.isEmpty() ? "N/A" : asString;
    }

    private String formatDate(Object dateValue) {
        if (dateValue == null) return "N/A";
        if (dateValue instanceof LocalDate localDate) {
            return DATE_FORMATTER.format(localDate);
        }
        try {
            return DATE_FORMATTER.format(LocalDate.parse(dateValue.toString()));
        } catch (Exception e) {
            return safeValue(dateValue);
        }
    }

    private String formatTime(Object timeValue) {
        if (timeValue == null) return "N/A";
        if (timeValue instanceof LocalTime localTime) {
            return TIME_FORMATTER.format(localTime);
        }
        try {
            return TIME_FORMATTER.format(LocalTime.parse(timeValue.toString()));
        } catch (Exception e) {
            return safeValue(timeValue);
        }
    }

    /**
     * Send booking notification email to driver
     *
     * @param driverEmail Driver's email address
     * @param driverName Driver's name
     * @param passengerName Passenger's name
     * @param passengerEmail Passenger's email address
     * @param passengerPhone Passenger's phone number (if available)
     * @param rideDetails Ride details
     * @param seatsBooked Number of seats booked
     */
    @Async
    public void sendBookingNotificationToDriver(
            String driverEmail,
            String driverName,
            String passengerName,
            String passengerEmail,
            String passengerPhone,
            Map<String, Object> rideDetails,
            Integer seatsBooked) {

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message,
                    MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                    StandardCharsets.UTF_8.name());

            helper.setFrom(Optional.ofNullable(defaultFromAddress).orElse("no-reply@smartridesharing.com"));
            helper.setTo(driverEmail);
            helper.setSubject("Smart Ride Sharing - New booking received");
            helper.setText(buildDriverEmailBody(driverName, passengerName, passengerEmail, passengerPhone, rideDetails, seatsBooked), true);

            log.info("Attempting to send booking notification email to driver: {}", driverEmail);
            mailSender.send(message);
            log.info("Booking notification email sent successfully to driver: {}", driverEmail);
        } catch (Exception ex) {
            log.error("Failed to send booking notification email to driver {}: {}", driverEmail, ex.getMessage(), ex);
        }
    }

    /**
     * Send ride completion OTP email to passenger
     * NOTE: This method is NOT @Async to ensure synchronous execution and proper error handling
     * OTP emails are critical and must be sent immediately with error feedback
     *
     * @param passengerEmail Passenger's email address
     * @param passengerName Passenger's name
     * @param source Ride source location
     * @param destination Ride destination location
     * @param rideDate Ride date
     * @param rideTime Ride time
     * @param otp 6-digit OTP code
     */
    public void sendRideCompletionOtp(
            String passengerEmail,
            String passengerName,
            String source,
            String destination,
            LocalDate rideDate,
            LocalTime rideTime,
            String otp) {

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message,
                    MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                    StandardCharsets.UTF_8.name());

            helper.setFrom(Optional.ofNullable(defaultFromAddress).orElse("no-reply@smartridesharing.com"));
            helper.setTo(passengerEmail);
            helper.setSubject("Smart Ride Sharing - Ride Completion Verification OTP");
            helper.setText(buildOtpEmailBody(passengerName, source, destination, rideDate, rideTime, otp), true);

            log.info("Attempting to send ride completion OTP email to passenger: {}", passengerEmail);
            log.info("Email details - From: {}, To: {}, Subject: Ride Completion Verification OTP, OTP: {}", 
                defaultFromAddress, passengerEmail, otp);
            
            mailSender.send(message);
            
            log.info("✅ Ride completion OTP email sent successfully to passenger: {}", passengerEmail);
            log.info("✅ OTP {} sent to {} for ride completion verification", otp, passengerEmail);
        } catch (Exception ex) {
            log.error("❌ FAILED to send ride completion OTP email to passenger {}: {}", passengerEmail, ex.getMessage(), ex);
            log.error("❌ Email error details - Exception type: {}, Cause: {}", 
                ex.getClass().getName(), ex.getCause() != null ? ex.getCause().getMessage() : "N/A");
            // Re-throw to let caller know email failed
            throw new RuntimeException("Failed to send OTP email: " + ex.getMessage(), ex);
        }
    }

    private String buildOtpEmailBody(String passengerName,
                                    String source,
                                    String destination,
                                    LocalDate rideDate,
                                    LocalTime rideTime,
                                    String otp) {
        return EmailTemplate.builder()
                .title("Ride Completion Verification")
                .greeting(String.format("Hi %s,", safeValue(passengerName)))
                .intro("The driver has marked your ride as completed. Please share the OTP below with the driver to confirm completion and release payment.")
                .addSection("Ride Details", buildRideDetailsForOtp(source, destination, rideDate, rideTime))
                .addSection("Verification OTP", buildOtpSection(otp))
                .footer("""
                        <strong>Important:</strong><br/>
                        • This OTP is valid for 10 minutes<br/>
                        • Share this OTP with your driver to complete the ride<br/>
                        • Once verified, the driver will receive payment<br/>
                        <br/>
                        If you did not complete this ride, please contact support immediately.<br/>
                        <br/>
                        Safe travels!<br/>Smart Ride Sharing Team
                        """)
                .build();
    }

    private String buildRideDetailsForOtp(String source, String destination, LocalDate rideDate, LocalTime rideTime) {
        return """
                <ul>
                    <li><strong>Route:</strong> %s → %s</li>
                    <li><strong>Date:</strong> %s</li>
                    <li><strong>Time:</strong> %s</li>
                </ul>
                """.formatted(
                safeValue(source),
                safeValue(destination),
                formatDate(rideDate),
                formatTime(rideTime)
        );
    }

    private String buildOtpSection(String otp) {
        return """
                <div style="text-align: center; padding: 20px; background: #f0f9ff; border-radius: 8px; border: 2px solid #0f8b8d;">
                    <p style="margin: 0 0 10px; font-size: 14px; color: #4b5563;">Your verification code:</p>
                    <p style="margin: 0; font-size: 32px; font-weight: bold; color: #0f8b8d; letter-spacing: 4px;">%s</p>
                    <p style="margin: 10px 0 0; font-size: 12px; color: #6b7280;">Valid for 10 minutes</p>
                </div>
                """.formatted(otp);
    }

    private String buildDriverEmailBody(String driverName,
                                        String passengerName,
                                        String passengerEmail,
                                        String passengerPhone,
                                        Map<String, Object> rideDetails,
                                        Integer seatsBooked) {
        return EmailTemplate.builder()
                .title("You have a new passenger")
                .greeting(String.format("Hi %s,", safeValue(driverName)))
                .intro("Great news! Someone just booked a seat on your ride.")
                .addSection("Ride Summary", buildRideSummary(rideDetails, seatsBooked, false))
                .addSection("Passenger Details", buildPassengerSummary(passengerName, passengerEmail, passengerPhone))
                .footer(defaultFooter())
                .build();
    }

    private String buildPassengerSummary(String passengerName, String passengerEmail, String passengerPhone) {
        String phoneLine = passengerPhone != null && !passengerPhone.trim().isEmpty()
                ? "<li><strong>Phone:</strong> %s</li>".formatted(passengerPhone)
                : "";
        return """
                <ul>
                    <li><strong>Name:</strong> %s</li>
                    <li><strong>Email:</strong> %s</li>
                    %s
                </ul>
                """.formatted(
                safeValue(passengerName),
                safeValue(passengerEmail),
                phoneLine
        );
    }

    /**
     * Send ride cancellation email to passenger
     *
     * @param passengerEmail Passenger's email address
     * @param passengerName Passenger's name
     * @param driverName Driver's name
     * @param rideDetails Ride details (source, destination, date, time, etc.)
     * @param reason Cancellation reason (optional)
     */
    @Async
    public void sendRideCancellationToPassenger(
            String passengerEmail,
            String passengerName,
            String driverName,
            Map<String, Object> rideDetails,
            String reason) {

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message,
                    MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                    StandardCharsets.UTF_8.name());

            helper.setFrom(Optional.ofNullable(defaultFromAddress).orElse("no-reply@smartridesharing.com"));
            helper.setTo(passengerEmail);
            helper.setSubject("Smart Ride Sharing - Ride Cancelled");
            helper.setText(buildCancellationEmailBody(passengerName, driverName, rideDetails, reason), true);

            log.info("Attempting to send ride cancellation email to passenger: {}", passengerEmail);
            mailSender.send(message);
            log.info("Ride cancellation email sent successfully to passenger: {}", passengerEmail);
        } catch (Exception ex) {
            log.error("Failed to send ride cancellation email to passenger {}: {}", passengerEmail, ex.getMessage(), ex);
        }
    }

    private String buildCancellationEmailBody(String passengerName,
                                             String driverName,
                                             Map<String, Object> rideDetails,
                                             String reason) {
        return EmailTemplate.builder()
                .title("Ride Cancelled")
                .greeting(String.format("Hi %s,", safeValue(passengerName)))
                .intro("We're sorry to inform you that the driver has cancelled the ride you booked.")
                .addSection("Cancelled Ride Details", buildRideSummary(rideDetails, null, true))
                .addSection("Driver Information", buildDriverSummary(driverName, "N/A"))
                .addSection("What's Next", buildCancellationNextSteps(reason))
                .footer("""
                        If you have any questions or concerns, please contact our support team.<br/>
                        <br/>
                        We apologize for any inconvenience.<br/>
                        <br/>
                        Smart Ride Sharing Team
                        """)
                .build();
    }

    private String buildCancellationNextSteps(String reason) {
        StringBuilder builder = new StringBuilder();
        builder.append("<ul>");
        builder.append("<li>Your booking has been automatically cancelled</li>");
        builder.append("<li>If you've already paid, a full refund will be processed within 3-5 business days</li>");
        builder.append("<li>You can search for alternative rides on our platform</li>");
        if (reason != null && !reason.trim().isEmpty()) {
            builder.append("<li><strong>Reason:</strong> ").append(reason).append("</li>");
        }
        builder.append("</ul>");
        return builder.toString();
    }

    /**
     * Send ride reschedule email to passenger
     *
     * @param passengerEmail Passenger's email address
     * @param passengerName Passenger's name
     * @param driverName Driver's name
     * @param rideDetails Ride details (source, destination, etc.)
     * @param oldDate Original ride date
     * @param oldTime Original ride time
     * @param newDate New ride date
     * @param newTime New ride time
     */
    @Async
    public void sendRideRescheduleToPassenger(
            String passengerEmail,
            String passengerName,
            String driverName,
            Map<String, Object> rideDetails,
            LocalDate oldDate,
            LocalTime oldTime,
            LocalDate newDate,
            LocalTime newTime) {

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message,
                    MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                    StandardCharsets.UTF_8.name());

            helper.setFrom(Optional.ofNullable(defaultFromAddress).orElse("no-reply@smartridesharing.com"));
            helper.setTo(passengerEmail);
            helper.setSubject("Smart Ride Sharing - Ride Rescheduled");
            helper.setText(buildRescheduleEmailBody(passengerName, driverName, rideDetails, oldDate, oldTime, newDate, newTime), true);

            log.info("Attempting to send ride reschedule email to passenger: {}", passengerEmail);
            mailSender.send(message);
            log.info("Ride reschedule email sent successfully to passenger: {}", passengerEmail);
        } catch (Exception ex) {
            log.error("Failed to send ride reschedule email to passenger {}: {}", passengerEmail, ex.getMessage(), ex);
        }
    }

    private String buildRescheduleEmailBody(String passengerName,
                                           String driverName,
                                           Map<String, Object> rideDetails,
                                           LocalDate oldDate,
                                           LocalTime oldTime,
                                           LocalDate newDate,
                                           LocalTime newTime) {
        return EmailTemplate.builder()
                .title("Ride Rescheduled")
                .greeting(String.format("Hi %s,", safeValue(passengerName)))
                .intro("The driver has rescheduled your booked ride. Please note the new date and time below.")
                .addSection("Ride Details", buildRideSummary(rideDetails, null, true))
                .addSection("Schedule Change", buildScheduleChange(oldDate, oldTime, newDate, newTime))
                .addSection("Driver Information", buildDriverSummary(driverName, "N/A"))
                .addSection("What's Next", """
                        <ul>
                            <li>Your booking remains confirmed for the new date and time</li>
                            <li>If the new schedule doesn't work for you, you can cancel your booking</li>
                            <li>No action is required if you can make the new time</li>
                        </ul>
                        """)
                .footer("""
                        If you have any questions, please contact the driver or our support team.<br/>
                        <br/>
                        Safe travels!<br/>Smart Ride Sharing Team
                        """)
                .build();
    }

    private String buildScheduleChange(LocalDate oldDate, LocalTime oldTime, LocalDate newDate, LocalTime newTime) {
        return """
                <ul>
                    <li><strong>Original Date & Time:</strong> %s at %s</li>
                    <li><strong>New Date & Time:</strong> <span style="color: #0f8b8d; font-weight: bold;">%s at %s</span></li>
                </ul>
                """.formatted(
                formatDate(oldDate),
                formatTime(oldTime),
                formatDate(newDate),
                formatTime(newTime)
        );
    }

    /**
     * Lightweight HTML builder for consistent email structure.
     */
    private static class EmailTemplate {
        private final String title;
        private final String greeting;
        private final String intro;
        private final java.util.List<Section> sections;
        private final String footer;

        private EmailTemplate(String title, String greeting, String intro, java.util.List<Section> sections, String footer) {
            this.title = title;
            this.greeting = greeting;
            this.intro = intro;
            this.sections = sections;
            this.footer = footer;
        }

        public static Builder builder() {
            return new Builder();
        }

        public String build() {
            StringBuilder body = new StringBuilder();
            body.append("""
                    <html>
                    <body style="font-family: 'Segoe UI', Arial, sans-serif; background-color:#f4f6fb; padding:24px;">
                      <table width="100%%" cellpadding="0" cellspacing="0">
                        <tr>
                          <td>
                            <table width="100%%" cellpadding="0" cellspacing="0" style="max-width:600px;margin:auto;background:#ffffff;border-radius:16px;padding:32px;box-shadow:0 10px 30px rgba(10,37,64,0.07);">
                              <tr>
                                <td style="text-align:center;">
                                  <h2 style="margin:0;color:#0f8b8d;font-size:24px;">%s</h2>
                                  <p style="color:#5f6b7c;margin-top:4px;">Smart Ride Sharing</p>
                                </td>
                              </tr>
                              <tr>
                                <td style="padding-top:16px;">
                                  <p style="font-size:16px;color:#111827;margin:0 0 12px;">%s</p>
                                  <p style="font-size:15px;color:#4b5563;margin:0;">%s</p>
                                </td>
                              </tr>
                    """.formatted(title, greeting, intro));

            for (Section section : sections) {
                body.append("""
                      <tr>
                        <td style="padding-top:20px;">
                          <h3 style="margin:0 0 8px;color:#0f8b8d;font-size:18px;">%s</h3>
                          <div style="background:#f9fafb;border-radius:12px;padding:16px;border:1px solid #e5e7eb;color:#111827;">
                            %s
                          </div>
                        </td>
                      </tr>
                    """.formatted(section.heading, section.content));
            }

            body.append("""
                              <tr>
                                <td style="padding-top:28px;">
                                  <p style="font-size:14px;color:#6b7280;margin:0;">%s</p>
                                </td>
                              </tr>
                            </table>
                          </td>
                        </tr>
                      </table>
                    </body>
                    </html>
                    """.formatted(footer));
            return body.toString();
        }

        private record Section(String heading, String content) {}

        static class Builder {
            private String title;
            private String greeting;
            private String intro;
            private final java.util.List<Section> sections = new java.util.ArrayList<>();
            private String footer = "";

            Builder title(String title) {
                this.title = title;
                return this;
            }

            Builder greeting(String greeting) {
                this.greeting = greeting;
                return this;
            }

            Builder intro(String intro) {
                this.intro = intro;
                return this;
            }

            Builder addSection(String heading, String htmlContent) {
                sections.add(new Section(heading, htmlContent));
                return this;
            }

            Builder footer(String footer) {
                this.footer = footer;
                return this;
            }

            String build() {
                EmailTemplate template = new EmailTemplate(title, greeting, intro, sections, footer);
                return template.build();
            }
        }
    }

    /**
     * Send ride reminder email to passenger
     *
     * @param passengerEmail Passenger's email address
     * @param passengerName Passenger's name
     * @param driverName Driver's name
     * @param driverEmail Driver's email address
     * @param rideDetails Ride details (source, destination, date, time, vehicle, etc.)
     * @param hoursBefore Hours before the ride
     */
    @Async
    public void sendRideReminderToPassenger(
            String passengerEmail,
            String passengerName,
            String driverName,
            String driverEmail,
            Map<String, Object> rideDetails,
            int hoursBefore) {

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message,
                    MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                    StandardCharsets.UTF_8.name());

            helper.setFrom(Optional.ofNullable(defaultFromAddress).orElse("no-reply@smartridesharing.com"));
            helper.setTo(passengerEmail);
            helper.setSubject(String.format("Smart Ride Sharing - Ride Reminder (%d hour%s before)", hoursBefore, hoursBefore > 1 ? "s" : ""));
            helper.setText(buildReminderEmailBody(passengerName, driverName, driverEmail, rideDetails, hoursBefore, true), true);

            log.info("Attempting to send ride reminder email to passenger: {}", passengerEmail);
            mailSender.send(message);
            log.info("Ride reminder email sent successfully to passenger: {}", passengerEmail);
        } catch (Exception ex) {
            log.error("Failed to send ride reminder email to passenger {}: {}", passengerEmail, ex.getMessage(), ex);
        }
    }

    /**
     * Send ride reminder email to driver
     *
     * @param driverEmail Driver's email address
     * @param driverName Driver's name
     * @param rideDetails Ride details (source, destination, date, time, vehicle, etc.)
     * @param bookingsCount Number of confirmed bookings
     * @param hoursBefore Hours before the ride
     */
    @Async
    public void sendRideReminderToDriver(
            String driverEmail,
            String driverName,
            Map<String, Object> rideDetails,
            int bookingsCount,
            int hoursBefore) {

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message,
                    MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                    StandardCharsets.UTF_8.name());

            helper.setFrom(Optional.ofNullable(defaultFromAddress).orElse("no-reply@smartridesharing.com"));
            helper.setTo(driverEmail);
            helper.setSubject(String.format("Smart Ride Sharing - Ride Reminder (%d hour%s before)", hoursBefore, hoursBefore > 1 ? "s" : ""));
            helper.setText(buildReminderEmailBody(driverName, driverName, driverEmail, rideDetails, hoursBefore, false, bookingsCount), true);

            log.info("Attempting to send ride reminder email to driver: {}", driverEmail);
            mailSender.send(message);
            log.info("Ride reminder email sent successfully to driver: {}", driverEmail);
        } catch (Exception ex) {
            log.error("Failed to send ride reminder email to driver {}: {}", driverEmail, ex.getMessage(), ex);
        }
    }

    private String buildReminderEmailBody(String recipientName,
                                          String driverName,
                                          String driverEmail,
                                          Map<String, Object> rideDetails,
                                          int hoursBefore,
                                          boolean isPassenger) {
        return buildReminderEmailBody(recipientName, driverName, driverEmail, rideDetails, hoursBefore, isPassenger, 0);
    }

    private String buildReminderEmailBody(String recipientName,
                                          String driverName,
                                          String driverEmail,
                                          Map<String, Object> rideDetails,
                                          int hoursBefore,
                                          boolean isPassenger,
                                          int bookingsCount) {
        String reminderText = isPassenger
                ? String.format("This is a friendly reminder that your ride is scheduled in <strong>%d hour%s</strong>.", hoursBefore, hoursBefore > 1 ? "s" : "")
                : String.format("This is a friendly reminder that your ride is scheduled in <strong>%d hour%s</strong> with <strong>%d confirmed booking%s</strong>.", hoursBefore, hoursBefore > 1 ? "s" : "", bookingsCount, bookingsCount != 1 ? "s" : "");

        return EmailTemplate.builder()
                .title("Ride Reminder")
                .greeting(String.format("Hi %s,", safeValue(recipientName)))
                .intro(reminderText)
                .addSection("Ride Details", buildRideSummary(rideDetails, null, true))
                .addSection("Driver Information", buildDriverSummary(driverName, driverEmail))
                .addSection("What's Next", buildReminderNextSteps(isPassenger))
                .footer("""
                        We look forward to providing you with a safe and comfortable ride.<br/>
                        <br/>
                        If you have any questions or need to make changes, please contact us.<br/>
                        <br/>
                        Safe travels!<br/>
                        <br/>
                        Smart Ride Sharing Team
                        """)
                .build();
    }

    private String buildReminderNextSteps(boolean isPassenger) {
        StringBuilder builder = new StringBuilder();
        builder.append("<ul>");
        if (isPassenger) {
            builder.append("<li>Please arrive at the pickup location on time</li>");
            builder.append("<li>Have your booking confirmation ready</li>");
            builder.append("<li>Contact the driver if you have any questions</li>");
            builder.append("<li>If you need to cancel, please do so as soon as possible</li>");
        } else {
            builder.append("<li>Please ensure your vehicle is ready and in good condition</li>");
            builder.append("<li>Review your route and estimated travel time</li>");
            builder.append("<li>Be prepared to pick up passengers on time</li>");
            builder.append("<li>Have your contact information ready for passengers</li>");
        }
        builder.append("</ul>");
        return builder.toString();
    }
}

