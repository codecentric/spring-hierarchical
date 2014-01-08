package de.codecentric.spring.hierarchical;

import com.vaadin.ui.UI;

public interface UIMapper {

	Class<? extends UI> map(String pathInfo);

}
