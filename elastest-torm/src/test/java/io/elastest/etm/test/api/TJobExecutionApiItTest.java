package io.elastest.etm.test.api;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

import javax.websocket.ContainerProvider;
import javax.websocket.WebSocketContainer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.opentest4j.MultipleFailuresError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandler;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import io.elastest.etm.ElasTestTormApp;
import io.elastest.etm.model.TJob;
import io.elastest.etm.model.TJobExecution;
import io.elastest.etm.model.TJobExecution.ResultEnum;

@RunWith(JUnitPlatform.class)
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = ElasTestTormApp.class, webEnvironment = WebEnvironment.RANDOM_PORT)
public class TJobExecutionApiItTest extends EtmApiItTest {

	private class WaitForMessagesHandler implements StompFrameHandler {

		private CountDownLatch latch = new CountDownLatch(1);
		private Predicate<String> messagePattern;

		public WaitForMessagesHandler(Predicate<String> messagePattern) {
			this.messagePattern = messagePattern;
		}

		public WaitForMessagesHandler() {
		}

		@Override
		public Type getPayloadType(StompHeaders stompHeaders) {
			return String.class;
		}

		@Override
		public void handleFrame(StompHeaders stompHeaders, Object msg) {

			log.info("Stomp message: " + msg);

			String strMsg = (String) msg;

			if (messagePattern == null || messagePattern.test(strMsg)) {
				log.info("Match message");
				latch.countDown();
			}
		}

		public void waitForCompletion() throws InterruptedException {
			int timeSeconds = 120;
			if (!latch.await(timeSeconds, TimeUnit.SECONDS)) {
				throw new RuntimeException("Timeout of " + timeSeconds + " waiting for message"
						+ (messagePattern == null ? "" : " with pattern"));
			}
		}
	}

	private class LogConnectedSessionHandler extends StompSessionHandlerAdapter {
		
		public void afterConnected(StompSession stompSession, StompHeaders stompHeaders) {
			log.info("STOMP Client connected");
		}

		@Override
		public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload,
				Throwable exception) {
			log.info("handleException: StopmSession:" + session + " StompCommand:" + command + " Exception:"
					+ exception);
		}

		@Override
		public void handleTransportError(StompSession session, Throwable exception) {
			log.info("handleTransportError: StopmSession:" + session + " Exception:" + exception);
		}
	}

	private static final Logger log = LoggerFactory.getLogger(TJobExecutionApiItTest.class);

	long projectId;

	@BeforeEach
	void setup() {
		log.info("App started on port {}", serverPort);
		projectId = createProject("Test_Project").getId();
	}

	@AfterEach
	void reset() {
		deleteProject(projectId);
	}

	@Test
	public void testExecuteTJobWithSut() throws InterruptedException, ExecutionException, TimeoutException {

		testExecuteTJob(true);
	}

	@Test
	public void testExecuteTJobWithoutSut() throws InterruptedException, ExecutionException, TimeoutException {

		testExecuteTJob(false);
	}

	private void testExecuteTJob(boolean withSut)
			throws InterruptedException, ExecutionException, TimeoutException, MultipleFailuresError {

		log.info("Start the test testExecuteTJob " + (withSut ? "with" : "without") + " SuT");

		TJob tJob;

		if (withSut) {
			Long sutId = createSut(projectId).getId();
			tJob = createTJob(projectId, sutId);
		} else {
			tJob = createTJob(projectId);
		}

		StompSession stompSession = connectToRabbitMQ();

		log.info("POST /api/tjob/{tjobId}/exec");

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);

		String body = "[{\"Param1\":\"NewValue1\"}]";
		HttpEntity<String> entity = new HttpEntity<>(body, headers);

		Map<String, Object> urlParams = new HashMap<>();
		urlParams.put("tjobId", tJob.getId());

		ResponseEntity<TJobExecution> response = httpClient.postForEntity("/api/tjob/{tjobId}/exec", entity,
				TJobExecution.class, urlParams);

		TJobExecution exec = response.getBody();

		log.info("TJobExecution creation response: " + response);

		if (withSut) {

			String queueToSuscribe = "/topic/" + "sut." + exec.getId() + ".log";
			log.info("Sut log queue '" + queueToSuscribe + "'");

			WaitForMessagesHandler handler = new WaitForMessagesHandler();
			stompSession.subscribe(queueToSuscribe, handler);

			handler.waitForCompletion();
			
			log.info("Sut log queue received a message");			
		}

		String queueToSuscribe = "/topic/" + "test." + exec.getId() + ".log";
		log.info("TJob log queue '" + queueToSuscribe + "'");
		
		WaitForMessagesHandler handler = new WaitForMessagesHandler(msg -> 			
			msg.contains("BUILD SUCCESS") || msg.contains("BUILD FAILURE"));		
		
		stompSession.subscribe(queueToSuscribe, handler);
		handler.waitForCompletion();

		assertAll("Validating TJobExecution Properties", () -> assertNotNull(response.getBody()),
				() -> assertNotNull(response.getBody().getId()),
				() -> assertTrue(response.getBody().getTjob().getId().equals(urlParams.get("tjobId"))));

		while (true) {
			exec = getTJobExecutionById(exec.getId(), tJob.getId()).getBody();
			log.info("TJobExecution: " + exec);
			if (exec.getResult() != ResultEnum.IN_PROGRESS) {
				
				log.info("Test results:"+exec.getTestSuite());
				
				break;
			}
			sleep(500);
		}

		deleteTJobExecution(exec.getId(), tJob.getId());
		deleteTJob(tJob.getId());
		log.info("Finished.");
	}

	private StompSession connectToRabbitMQ() throws InterruptedException, ExecutionException, TimeoutException {
		WebSocketContainer cont = ContainerProvider.getWebSocketContainer();
		cont.setDefaultMaxTextMessageBufferSize(65500);
		WebSocketClient webSocketClient = new StandardWebSocketClient(cont);

		WebSocketStompClient stompClient = new WebSocketStompClient(webSocketClient);
		stompClient.setMessageConverter(new StringMessageConverter());
		ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
		taskScheduler.initialize();
		stompClient.setTaskScheduler(taskScheduler); // for heartbeats
		stompClient.setDefaultHeartbeat(new long[] { 10000, 10000 });

		String url = "ws://localhost:" + serverPort + "/rabbitMq";
		StompSessionHandler sessionHandler = new LogConnectedSessionHandler();
		StompSession stompSession = stompClient.connect(url, sessionHandler).get(10, TimeUnit.SECONDS);
		return stompSession;
	}

	private void sleep(int waitTime) {
		try {
			Thread.sleep(waitTime);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

}
