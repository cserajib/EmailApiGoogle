package com.example.demo;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Properties;

import javax.mail.MessagingException;
//import javax.mail.internet.MimeMessage;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import com.example.demo.dto.AccessTokenResponse;
import com.example.demo.entity.OAuthToken;
import com.example.demo.repository.TokenRepository;

import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.HttpServletResponse;

import com.sun.mail.smtp.SMTPTransport;

@RestController
@RequestMapping("/gmail")
public class GmailOauthController {
	
	//@Autowired
	//RestTemplate restTemplate = new RestTemplate();
	
	@Autowired
	TokenRepository tokenRepository;
	
	private static final String AUTH_URL ="https://accounts.google.com/o/oauth2/v2/auth?";
	private static final String REDIRECT_URI ="http://localhost:8080/helpdesk/gmail/oauth-redirect";
	private static final String CLIENT_ID ="";
	private static final String CLIENT_SECRET ="";
	private static final String SENDER_EMAIL ="rajib.devops@gmail.com";
	private static final String RECIPIENT_EMAIL ="howrah.rajib@gmail.com";
	private static final String TOKEN_URL ="https://oauth2.googleapis.com/token";
	private static final String SCOPE ="https://mail.google.com";
	private static final String ACCESS_TYPE ="offline";
	private static final String PROMPT ="consent";
	
	private static final String GMAIL_SMTP ="smtp.gmail.com";
	private static final String CODE ="code";
	
	@GetMapping("/authorize")
	public void authorize(HttpServletResponse response)throws IOException{
		
		String url = AUTH_URL +
				"client_id=" +CLIENT_ID+
				"&redirect_uri=" +REDIRECT_URI+
				"&response_type=" +CODE+
				"&scope=" +SCOPE+
				"&access_type=" +ACCESS_TYPE+
				"&prompt=" +PROMPT;
				

		response.sendRedirect(url);
	}
	
	@GetMapping("/oauth-redirect")
	public ResponseEntity<String> handleRedirect(@RequestParam("code") String code){
		
		MultiValueMap< String, String> params = new LinkedMultiValueMap<>();
		
		params.add("code", code);
		params.add("client_id", CLIENT_ID);
		params.add("client_secret", CLIENT_SECRET);
		params.add("redirect_uri", REDIRECT_URI);
		params.add("grant_type", "authorization_code");
		
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
		
		HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params,headers);
		
		//ResponseEntity<AccessTokenResponse> response = restTemplate.postForEntity(TOKEN_URL, request, AccessTokenResponse.class);
		
		WebClient webClient = WebClient.builder().baseUrl(TOKEN_URL).defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE).build();
		AccessTokenResponse token = webClient.post().bodyValue(params).retrieve().bodyToMono(AccessTokenResponse.class).block();
		
		//AccessTokenResponse token = response.getBody();
		if(token != null && token.getRefreshToken() !=null) {
			
			OAuthToken authToken = new OAuthToken();
			
			authToken.setEmail(SENDER_EMAIL);
			authToken.setRefreshToken(token.getRefreshToken());
			authToken.setStatus("ACTIVE");
			authToken.setCreatedBy("Rajib");
			authToken.setCreatedDate(LocalDateTime.now());
			
			tokenRepository.save(authToken);
			
			return ResponseEntity.ok("Refresh Token saved successfully");
			
		}
		
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("failed to obtaion or save refresh token");
	}
	
	private String getAccessToken(String refreshToken) {
			
	MultiValueMap< String, String> params = new LinkedMultiValueMap<>();
			
			
			params.add("client_id", CLIENT_ID);
			params.add("client_secret", CLIENT_SECRET);
			params.add("refresh_token", refreshToken);
			params.add("grant_type", "refresh_token");
			
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
			
			HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params,headers);
			
			//ResponseEntity<AccessTokenResponse> response = restTemplate.postForEntity(TOKEN_URL, request, AccessTokenResponse.class);
			
			WebClient webClient = WebClient.builder().baseUrl(TOKEN_URL).defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE).build();
			AccessTokenResponse response = webClient.post().bodyValue(params).retrieve().bodyToMono(AccessTokenResponse.class).block();
			
			return response.getAccessToken();
			
	}
	
	private void sendEmailUsingOAuth(String accessToken)throws MessagingException, AddressException, jakarta.mail.MessagingException{
		
		Properties props = new Properties();
		
		props.put("mail.smtp.auth", "true");
		props.put("mail.smtp.auth.mechanisms", "XOAUTH2");
		props.put("mail.smtp.starttls.enable", "true");
		props.put("mail.smtp.starttls.required", "true");
		props.put("mail.smtp.host", "smtp.gmail.com");
		props.put("mail.smtp.port", "587");
		
		Session session = Session.getInstance(props);
		session.setDebug(true);
		
		MimeMessage message = new MimeMessage(session);
		
		message.setFrom(new InternetAddress(SENDER_EMAIL));
		message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(RECIPIENT_EMAIL));
		message.setSubject("test mail with oauth2....");
		message.setText("this email sent using gmail smtp and oauth2");
		
		Transport smtpTransport =session.getTransport("smtp");	
		
		smtpTransport.connect(GMAIL_SMTP,SENDER_EMAIL,accessToken);
		smtpTransport.sendMessage(message, message.getAllRecipients());
		smtpTransport.close();
		
		
	}
	
	
	@GetMapping("/send-mail")
	public ResponseEntity<String> sendEmail(){
		try {
			
		Optional<OAuthToken> optional = tokenRepository.findByEmail(SENDER_EMAIL);
		if(!optional.isPresent()) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Refresh Token not found");
		}
		
		String refreshToken = optional.get().getRefreshToken();
		String accessToken = getAccessToken(refreshToken);
		
		sendEmailUsingOAuth(accessToken);
		
		return ResponseEntity.ok("Email sent successfully");
		
		
	    }catch(Exception ex) {
	    	return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("error sending email"+ex.getMessage());
	 }
		
		
  }
}
