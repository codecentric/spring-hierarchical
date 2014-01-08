package de.codecentric.spring.hierarchical;

import com.vaadin.server.UIClassSelectionEvent;
import com.vaadin.server.UICreateEvent;
import com.vaadin.server.UIProvider;
import com.vaadin.server.VaadinRequest;
import com.vaadin.ui.UI;


public class SpringUIProvider extends UIProvider {
	
	private static final long serialVersionUID = -2978459901046918860L;
	private HierarchicalContext rootContext;
	private UIMapper uiMapper;

	public SpringUIProvider(HierarchicalContext rootContext, UIMapper uiMapper) {
		this.rootContext = rootContext;
		this.uiMapper = uiMapper;
	}

	@Override
	public UI createInstance(UICreateEvent event) {
		return rootContext.createBeanInSubContext(getUiClass(event.getRequest()));
	}
	
	private Class<? extends UI> getUiClass(VaadinRequest request) {
		String pathInfo = request.getPathInfo();
		return uiMapper.map(pathInfo); 
	}

	@Override
	public Class<? extends UI> getUIClass(UIClassSelectionEvent event) {
		return getUiClass(event.getRequest());
	}
	
}
