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
}

