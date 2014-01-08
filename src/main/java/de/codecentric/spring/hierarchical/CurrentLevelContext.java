package de.codecentric.spring.hierarchical;

public interface CurrentLevelContext {
	<T> T createBeanInSubContext(Class<T> typeOfBeanToCreate);

	Object createBeanInSubContext(String name);
}
