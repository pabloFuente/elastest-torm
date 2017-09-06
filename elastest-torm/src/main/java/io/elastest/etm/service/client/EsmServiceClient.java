package io.elastest.etm.service.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.node.ObjectNode;

import io.elastest.etm.model.ServiceInstance;
import io.elastest.etm.utils.UtilTools;


@Service
public class EsmServiceClient {
	private static final Logger logger = LoggerFactory.getLogger(EsmServiceClient.class);
	
	@Value("${services.ip}")
	private String esmIp;
	
	@Value("${elastest.esm.port}")
	private String esmPort;
	
	@Value("${elastest.esm.url.register.service}")
	private String URL_ESM_REGISTER_SERVICE;
	
	@Value("${elastest.esm.url.catalog}")
	private String URL_GET_CATALOG_ESM;
	
	@Value("${elastest.esm.url.register.manifest}")
	private String URL_ESM_REGISTER_MANIFEST;

	@Value("${elastest.esm.url.request.instance}")
	private String URL_ESM_PROVISION_INSTANCE;
	@Value("${elastest.esm.url.deprovision.instance}")
	private String URL_ESM_DEPROVISION_INSTANCE;
	
	public UtilTools utilTools;
	
	RestTemplate httpClient;
	HttpHeaders headers;
	
	public EsmServiceClient(UtilTools utilTools){
		httpClient = new RestTemplate();
		headers = new HttpHeaders();
		List<MediaType> mediaTypes = new ArrayList<MediaType>();
		mediaTypes.add(MediaType.APPLICATION_JSON);		
		headers.setAccept(mediaTypes);
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.set("x-broker-api-version", "2.12");
		this.utilTools = utilTools;
	}
	
	/**
	 * 
	 * @param serviceRegistry
	 */
	public void registerService(String serviceRegistry){		
		logger.info("Registering the service in the ESM.");
		HttpEntity<String> entity = new HttpEntity<String>(serviceRegistry, headers);
				
		try{
			httpClient.put(URL_ESM_REGISTER_SERVICE, entity);
			logger.info("Registered service." );
		}catch(Exception e){
			logger.error(e.getMessage());
		}		
	}
	
	/**
	 * 
	 * @param serviceManifest
	 * @param id
	 * @return
	 */
	public String registerManifest(String serviceManifest, String id){
		logger.info("Registering the service manifest in the ESM.");
		HttpEntity<String> entity = new HttpEntity<String>(serviceManifest, headers);		
		
		Map<String, String> params = new HashMap<>();
		params.put("manifest_id", id);
		
		UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(URL_ESM_REGISTER_MANIFEST);
		ResponseEntity<ObjectNode> result = null;
		try{						
			result = httpClient.exchange(builder.buildAndExpand(params).toUri(), HttpMethod.PUT, entity, ObjectNode.class);
		}catch(Exception e){
			if (result != null && result.getBody().toString().equals("\"duplicate\"")){				
				logger.info("The manifest already exists:" + e.getMessage());
			}else{
				e.printStackTrace();								
			}			
		}
		logger.info("After send:" );
		
		return "";
	}
	
	/**
	 * 
	 * @param reqServiceInstance
	 * @param instance_id
	 * @param accept_incomplete
	 * @return
	 */
	public String provisionServiceInstance(ServiceInstance serviceInstance, String instance_id, String accept_incomplete){
		String serviceInstanceData = "";
		logger.info("Requesting a service instance.");
		HttpEntity<String> entity = new HttpEntity<String>(utilTools.convertJsonString(serviceInstance), headers);
		
		Map<String, String> params = new HashMap<>();
		params.put("instance_id", instance_id);
		//params.put("accept_incomplete", accept_incomplete);
		UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(URL_ESM_PROVISION_INSTANCE)
				.queryParam("accept_incomplete", accept_incomplete);
		
		ResponseEntity<ObjectNode> result = null;
				
		try{
			result = httpClient.exchange(builder.buildAndExpand(params).toUri(), HttpMethod.PUT, entity, ObjectNode.class);
			logger.info("Registered service." );
		}catch(Exception e){
			logger.error(e.getMessage());			
		}		
		
		return serviceInstanceData;		
	}
	
	public void deprovisionServiceInstance(String instance_id, ServiceInstance serviceInstance){
		String serviceInstanceData = "";
		logger.info("Requesting a service instance.");		
		
		Map<String, String> params = new HashMap<>();
		params.put("instance_id", instance_id);
//		params.put("service_id", serviceInstance.getService_id());
//		params.put("plan_id", serviceInstance.getPlan_id());
//		params.put("accept_incomplete", Boolean.toString(false));
				
		UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(URL_ESM_DEPROVISION_INSTANCE)
				.queryParam("service_id", serviceInstance.getService_id())
				.queryParam("plan_id", serviceInstance.getPlan_id())
				.queryParam("accept_incomplete", Boolean.toString(false));
		
		ResponseEntity<ObjectNode> result = null;		
		try{
			result = httpClient.exchange(builder.buildAndExpand(params).toUri(), HttpMethod.DELETE, null, ObjectNode.class);
			logger.info("Registered service." );
		}catch(Exception e){
			logger.error(e.getMessage());
		}				
	}
	
	/**
	 * 
	 * @return
	 */
	public String getCatalogedServices() {
		logger.info("Retrieving the services.");
		HttpHeaders headers = new HttpHeaders();
		headers.set("x-broker-api-version", "2.12");
		HttpEntity<String> entity = new HttpEntity<String>(headers);
				
		try{
			ResponseEntity<ObjectNode> objNode = httpClient.exchange(URL_GET_CATALOG_ESM, HttpMethod.GET, entity, ObjectNode.class);
			logger.info("Retrieved services.");
			return objNode.getBody().get("services").toString();
		}catch(Exception e){
			logger.error(e.getMessage());
			return null;
		}		
	}

}
