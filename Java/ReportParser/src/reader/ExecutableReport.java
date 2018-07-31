package reportparser.reader;

import org.quartz.JobDataMap;
import org.quartz.JobExecutionException;

public interface ExecutableReport
{
	public void execute(JobDataMap job_data_map) throws JobExecutionException;
}
