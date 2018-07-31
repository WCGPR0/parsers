package reportparser;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

public class ReportReaderJob implements Job
{
	private static Logger logger = LogManager.getLogger(ReportReaderJob.class);
	private String jobid;

	public void setJobID(String value)
	{
		jobid = value;
	}

	@Override
	public void execute(JobExecutionContext job_execution_context) throws JobExecutionException
	{
		JobDataMap job_data_map = job_execution_context.getMergedJobDataMap();
		ReportReader reportreader = Controller.getInstance().getReportReaders().get(jobid);
		if (reportreader!=null)
		{
			if (reportreader instanceof ExecutableReport)
			{
				//run report
				((ExecutableReport)reportreader).execute(job_data_map);
			}
			else
			{
				logger.error("Unable to execute report. ReportReader with ID {} does not implement ExecutableReport interface", jobid);
			}
		}
		else
		{
			logger.error("Unable to execute report. No ReportReader found with ID {}", jobid);
		}


	}

}
