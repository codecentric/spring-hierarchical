package de.codecentric.spring.hierarchical;

import org.springframework.beans.FatalBeanException;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.Scope;

public class HierarchicalThreadScope implements Scope {
	public static final String SCOPE_NAME = "hierarchical";
	private static ThreadLocal<HierarchicalContext> threadLocalContext = new ThreadLocal<HierarchicalContext>();

	public static void setCurrentThreadLocalContext(
			HierarchicalContext hierarchicalContext) {
		threadLocalContext.set(hierarchicalContext);
	}
	
	public static void unsetCurrentThreadLocalContext() {
		threadLocalContext.remove();
		threadLocalContext = null; 
	}
	

	@Override
	public Object get(String name, ObjectFactory<?> objectFactory) {
		Object object = currentContext().get(name);
		if (object == null) {
			object = objectFactory.getObject();
			targetContext(object).put(name, object);
		}
		return object;
	}

	private HierarchicalContext currentContext() {
		return threadLocalContext.get();
	}

	public HierarchicalContext targetContext(Object object) {
		String annotatedTargetContextLevel = annotatedTargetContextBeanName(object);
		return annotatedTargetContextLevel == null ? currentContext()
				: lookupAnnotatedTargetContext(object,
						annotatedTargetContextLevel);
	}

	private HierarchicalContext lookupAnnotatedTargetContext(Object object,
			String annotatedTargetContextLevel) {
		HierarchicalContext targetContext = currentContext().contextOfBean(
				annotatedTargetContextLevel);
		if (targetContext == null) {
			throw new FatalBeanException(
					"no bean named "
							+ annotatedTargetContextLevel
							+ " defined in @"
							+ TargetContextLevelOf.class.getSimpleName()
							+ " in "
							+ object.getClass()
							+ " was found in any parent context. "
							+ object.getClass()
							+ " has to be created in a sub context of the context containing "
							+ annotatedTargetContextLevel);
		}
		return targetContext;
	}

	public String annotatedTargetContextBeanName(Object object) {
		TargetContextLevelOf annotaton = object.getClass().getAnnotation(
				TargetContextLevelOf.class);
		if (annotaton == null) {
			return null;
		}
		String targetBeanName = annotaton.beanName();
		boolean beanNameIsAssigned = targetBeanName != null
				&& !targetBeanName
						.equalsIgnoreCase(TargetContextLevelOf.UNASSIGNED);
		if (beanNameIsAssigned) {
			return targetBeanName;
		}
		Class<?> targetClass = annotaton.value();
		return currentContext().beanNameForType(targetClass);
	}

	/*
	 * Keine erweiterte Scope Funktionalität umgesetzt, mangels zu lösendem
	 * Problem (keine session persistierung nötig)
	 */
	@Override
	public void registerDestructionCallback(String name, Runnable callback) {
	}

	@Override
	public Object resolveContextualObject(String key) {
		return null;
	}

	@Override
	public String getConversationId() {
		return null;
	}

	@Override
	public Object remove(String name) {
		return null;
	}
}