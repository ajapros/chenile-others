package org.chenile.scheduler.test.support;

public class KubernetesJobProperties {
	private String namespace = "default";
	private String workerImage;
	private String serviceAccount;

	public String getNamespace() {
		return namespace;
	}

	public void setNamespace(String namespace) {
		this.namespace = namespace;
	}

	public String getWorkerImage() {
		return workerImage;
	}

	public void setWorkerImage(String workerImage) {
		this.workerImage = workerImage;
	}

	public String getServiceAccount() {
		return serviceAccount;
	}

	public void setServiceAccount(String serviceAccount) {
		this.serviceAccount = serviceAccount;
	}
}
