package reportparser;

import org.apache.log4j.Logger;

public class ServiceExecutor extends ServiceExecutorBase {

	private static Logger logger = Logger.getLogger(ServiceExecutor.class);

	public ServiceExecutor(ServiceBase servicebase) {
		super(servicebase);
	}

	@Override
	public void buildServiceCommands() {
		m_serviceCommands.add("doSomething");
	}


	public Boolean doSomething(IConnection connection, String szobjectid, Parameter[] parameters)
	{
		String input = parameters[1].getValue();
		String output = "Hello " + input;
		connection.write("<success>" + output + "</success>");
		return true;
	}

}