package org.chenile.filewatch.model;

import org.chenile.core.model.ModuleAware;

public class FileWatchDefinition implements ModuleAware{
	@Override
	public String toString() {
		return "FileWatchDefinition [fileWatchId=" + fileWatchId + ", eventId=" + getEventId() + ", originatingModuleName=" + originatingModuleName
				+ ", recordClass=" + recordClass + ", dirToWatch=" + dirToWatch 
				+ "]";
	}
	public static final String EXTENSION = "fileWatch"; // register as a filewatch extension for chenile 
	
	private String fileWatchId;
	/** The event each record fires; subscribers subscribe to it. Defaults to the file watch id. */
	private String eventId;
	private String originatingModuleName;
	/**
	 * The class each record in the file is read as. Subscribers receive it converted to their own
	 * body type.
	 */
	private Class<?> recordClass;
	public String getFileWatchId() {
		return fileWatchId;
	}
	public void setFileWatchId(String fileWatchId) {
		this.fileWatchId = fileWatchId;
	}
	public String getEventId() {
		return eventId == null || eventId.isBlank() ? fileWatchId : eventId;
	}
	public void setEventId(String eventId) {
		this.eventId = eventId;
	}
	public String getDirToWatch() {
		return dirToWatch;
	}
	public void setDirToWatch(String dirToWatch) {
		this.dirToWatch = dirToWatch;
	}

	public String getOriginatingModuleName() {
		return originatingModuleName;
	}
	public void setOriginatingModuleName(String originatingModuleName) {
		this.originatingModuleName = originatingModuleName;
	}
	public Class<?> getRecordClass() {
		return recordClass;
	}
	public void setRecordClass(Class<?> recordClass) {
		this.recordClass = recordClass;
	}
	private String dirToWatch;
	
	private String templateName;
	private String template;
	private String description;
	/**
	 * @return the template
	 */
	public String getTemplate() {
		return template;
	}
	/**
	 * @param template the template to set
	 */
	public void setTemplate(String template) {
		this.template = template;
	}
	/**
	 * @return the description
	 */
	public String getDescription() {
		return description;
	}
	/**
	 * @param description the description to set
	 */
	public void setDescription(String description) {
		this.description = description;
	}
	/**
	 * @return the templateName
	 */
	public String getTemplateName() {
		return templateName;
	}
	/**
	 * @param templateName the templateName to set
	 */
	public void setTemplateName(String templateName) {
		this.templateName = templateName;
	}
	
}
