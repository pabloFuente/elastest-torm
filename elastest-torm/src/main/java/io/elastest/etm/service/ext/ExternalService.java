package io.elastest.etm.service.ext;

import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.elastest.etm.api.ext.model.ExternalJob;
import io.elastest.etm.api.ext.model.ExternalRabbitConfig;
import io.elastest.etm.api.model.Project;
import io.elastest.etm.api.model.TJob;
import io.elastest.etm.api.model.TJobExecution;
import io.elastest.etm.service.project.ProjectService;
import io.elastest.etm.service.tjob.TJobService;
import io.elastest.etm.utils.UtilTools;

@Service
public class ExternalService {
	
	private static final Logger logger = LoggerFactory.getLogger(ExternalService.class);
	
	private ProjectService projectService;
	private TJobService tJobService;
	private UtilTools utilTools;
	
	@Value("${os.name}")
	private String windowsSO;
	
	@Value("${server.port}")
	private String serverPort;
	
	@Value("${elastest.elasticsearch.host}")
	private String elasticsearchUrl;
	
	@Value("${elastest.torm-gui.port}")
	private String tormGuiPort;
	
	public ExternalService(ProjectService projectService, TJobService tJobService, UtilTools utilTools){
		super();
		this.projectService = projectService;
		this.tJobService = tJobService;
		this.utilTools = utilTools;
	}
	
	public ExternalJob createElasTestEntitiesForExtJob(ExternalJob externalJob) throws Exception{
		
		logger.info("Creating external job entities.");

		try{
		//Create ElasTest Project
		logger.info("Creating Project.");
		
		Project project = projectService.getProjectByName(externalJob.getJobName());
		if (project == null){
			project = new Project();
			project.setId(0L);
			project.setName(externalJob.getJobName());
			project = projectService.createProject(project);
		}
		
		//Create ElasTest TJob
		logger.info("Creating TJob.");
		TJob tJob = tJobService.getTJobByName(externalJob.getJobName());
		if (tJob == null){
			tJob = new TJob();
			tJob.setName(externalJob.getJobName());
			tJob = tJobService.createTJob(tJob);
			tJob.setProject(project);
			tJob.setExternal(true);			
		}		
		
		//Run ElasTest TJob
		logger.info("Creating TJobExecution.");
		TJobExecution tJobExecution = tJobService.executeTJob(tJob.getId(), new ArrayList<>() );
		
		String elasTestHostIP = null;		
				
		if (windowsSO.toLowerCase().contains("win")) {
			logger.info("Execute on Windows.");
			elasTestHostIP = utilTools.getElasTestHostOnWin();
			externalJob.setExecutionUrl("http://" + elasTestHostIP + ":" + tormGuiPort + "/#projects/" + project.getId() + "/tjob/" + tJob.getId() + "/tjob-exec/" + tJobExecution.getId() + "/dashboard");
			externalJob.setLogAnalyzerUrl("http://"+ elasTestHostIP + ":" + tormGuiPort + "#/logmanager?indexName=" + tJobExecution.getId());
		}else{
			externalJob.setExecutionUrl(":" + tormGuiPort + "/#projects/" + project.getId() + "/tjob/" + tJob.getId() + "/tjob-exec/" + tJobExecution.getId() + "/dashboard");
			externalJob.setLogAnalyzerUrl(":" + tormGuiPort + "#/logmanager?indexName=" + tJobExecution.getId());
		}
	
		externalJob.settJobExecId(tJobExecution.getId());
		
		externalJob.setElasticsearchUrl(elasticsearchUrl);
		externalJob.setRabbitMqconfig(new ExternalRabbitConfig());
		
		logger.info("TJobExecutino URL:" + externalJob.getExecutionUrl());
		}catch (Exception e){
			e.printStackTrace();
			logger.error("Error message: "+ e.getMessage());
			throw e;		
		}
		return externalJob;
		
	}

}
