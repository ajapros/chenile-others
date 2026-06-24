package org.chenile.scheduler.launcher;

import org.chenile.core.context.ChenileExchange;
import org.chenile.core.context.HeaderUtils;
import org.chenile.core.entrypoint.ChenileEntryPoint;
import org.chenile.scheduler.Constants;
import org.chenile.scheduler.model.ScheduledExecutionRequest;

public class LocalChenileTaskLauncher implements ScheduledTaskLauncher {
	public static final String LAUNCHER_NAME = "local";
	private final ChenileEntryPoint chenileEntryPoint;

	public LocalChenileTaskLauncher(ChenileEntryPoint chenileEntryPoint) {
		this.chenileEntryPoint = chenileEntryPoint;
	}

	@Override
	public String launcherName() {
		return LAUNCHER_NAME;
	}

	@Override
	public LaunchResult launch(ScheduledExecutionRequest request) {
		ChenileExchange exchange = new ChenileExchange();
		exchange.setHeader(HeaderUtils.ENTRY_POINT, Constants.SCHEDULER_ENTRY_POINT);
		exchange.setHeader(Constants.EXECUTION_ID, request.getExecutionId());
		exchange.setServiceDefinition(request.getServiceDefinition());
		exchange.setOperationDefinition(request.getOperationDefinition());
		if (request.getHeaders() != null) {
			request.getHeaders().forEach(exchange::setHeader);
		}
		exchange.setBody(request.getPayload());
		chenileEntryPoint.execute(exchange);
		return new LaunchResult("local");
	}
}
