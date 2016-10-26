package org.recap.camel;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.commons.io.FileUtils;
import org.recap.ReCAPConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;

/**
 * Created by chenchulakshmig on 13/9/16.
 */
@Component
public class EmailRouteBuilder {

    private String emailBody;
    private String emailPassword;

    @Autowired
    public EmailRouteBuilder(CamelContext context, @Value("${data.dump.email.username}") String username, @Value("${data.dump.email.password.file}") String passwordDirectory,
                             @Value("${data.dump.email.from}") String from, @Value("${data.dump.email.subject}") String subject,
                             @Value("${smtpServer}") String smtpServer) {
        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    loadEmailBodyTemplate();
                    loadEmailPassword();

                    from(ReCAPConstants.EMAIL_Q)
                            .routeId(ReCAPConstants.EMAIL_ROUTE_ID)
                            .setHeader("subject", simple(subject))
                            .setHeader("emailPayLoad").body(EmailPayLoad.class)
                            .setBody(simple(emailBody))
                            .setHeader("from", simple(from))
                            .setHeader("to", simple("${header.emailPayLoad.to}"))
                            .to("smtps://" + smtpServer + "?username=" + username + "&password=" + emailPassword)
                            .onCompletion().log("Email has been sent successfully.")
                            .end();
                }

                private void loadEmailBodyTemplate() {
                    InputStream inputStream = getClass().getResourceAsStream("email_body.vm");
                    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                    StringBuilder out = new StringBuilder();
                    String line;
                    try {
                        while ((line = reader.readLine()) != null) {
                            if (line.isEmpty()) {
                                out.append("\n");
                            } else {
                                out.append(line);
                                out.append("\n");
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    emailBody = out.toString();
                }

                private void loadEmailPassword() {
                    File file = new File(passwordDirectory);
                    if (file.exists()) {
                        try {
                            emailPassword = FileUtils.readFileToString(file, "UTF-8").trim();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
