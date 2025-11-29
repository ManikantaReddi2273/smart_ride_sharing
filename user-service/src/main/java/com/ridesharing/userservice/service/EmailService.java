package com.ridesharing.userservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Email Service responsible for sending transactional emails such as OTPs.
 */
@Service
@Slf4j
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    /**
     * Send a 4-digit OTP to the given recipient.
     *
     * @param to  recipient email
     * @param otp numeric OTP
     */
    @Async("mailExecutor")
    public void sendOtpEmail(String to, String otp) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("Smart Ride Sharing - OTP Verification");
        message.setText(String.format(
                "Dear user,%n%nYour verification code is %s.%nThis code expires in 10 minutes.%n%n"
                        + "If you did not attempt to register, please ignore this email.%n%n"
                        + "Regards,%nSmart Ride Sharing Team",
                otp));

        try {
            mailSender.send(message);
            log.info("OTP email sent successfully to {}", to);
        } catch (Exception ex) {
            log.error("Failed to send OTP email to {}: {}", to, ex.getMessage());
        }
    }
}

