package de.codecentric.spring.hierarchical;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.ObjectFactory;

public class HierarchicalThreadScopeTest {

	private HierarchicalContext childContext;
	private HierarchicalThreadScope scope;
	private ListableBeanFactory springContext;
	private HierarchicalContext parentContext;

	@Before
	public void setUp() {
		springContext = Mockito.mock(ListableBeanFactory.class);
		parentContext = new HierarchicalContext(null, springContext);
		childContext = new HierarchicalContext(parentContext, springContext);
		HierarchicalThreadScope.setCurrentThreadLocalContext(childContext);
		scope = new HierarchicalThreadScope();
	}

	@Test
	public void noAnnotationNoTargetBeanName() throws Exception {
		assertNull(scope.annotatedTargetContextBeanName(new Object()));
	}

	@Test
	public void classAnnotatedWithName() throws Exception {
		assertEquals(
				MyBeanAnnotatedWithBeanName.MY_BEAN_NAME,
				scope.annotatedTargetContextBeanName(new MyBeanAnnotatedWithBeanName()));
	}

	@TargetContextLevelOf(value = TargetBean.class, beanName = MyBeanAnnotatedWithBeanName.MY_BEAN_NAME)
	private static class MyBeanAnnotatedWithBeanName {
		static final String MY_BEAN_NAME = "myBeanName";
	}

	@Test
	public void classAnnotatedWithClass() throws Exception {
		String nameForTypeFromSpring = "MyBeanAnnotatedWithClassBeanName";
		Mockito.when(springContext.getBeanNamesForType(TargetBean.class))
				.thenReturn(new String[] { nameForTypeFromSpring });
		assertEquals(
				nameForTypeFromSpring,
				scope.annotatedTargetContextBeanName(new MyBeanAnnotatedWithClass()));
	}

	@Test
	public void noAnnotationTargetContextIsCurrentContext() throws Exception {
		assertSame(childContext, scope.targetContext(new Object()));
	}

	@Test
	public void createNewBeanBySpringInCurrentChildContext() throws Exception {
		Object object = new Object();
		assertSame(object,
				scope.get("name", springObjectFactoryReturning(object)));
		assertSame(object, childContext.get("name"));
		assertNull(parentContext.get("name"));
	}

	@Test
	public void createBeanWithNameOnlyOnceInScope() throws Exception {
		Object firstInstance = new Object();
		assertSame(firstInstance,
				scope.get("name", springObjectFactoryReturning(firstInstance)));
		assertSame(firstInstance,
				scope.get("name", springObjectFactoryReturning(new Object())));
	}

	@Test
	public void targetBeanInParentContext() throws Exception {
		TargetBean targetBeanWithMyBeanName = new TargetBean();
		String myBeanName = MyBeanAnnotatedWithBeanName.MY_BEAN_NAME;
		parentContext.put(myBeanName, targetBeanWithMyBeanName);
		childContext.put("anotherNameThatIsNotTargeted", new TargetBean());
		assertSame(targetBeanWithMyBeanName, scope.get(myBeanName, null));
	}

	@TargetContextLevelOf(value = TargetBean.class)
	private static class MyBeanAnnotatedWithClass {
	}

	private static class TargetBean {
	}

	private ObjectFactory<?> springObjectFactoryReturning(final Object object) {
		ObjectFactory<?> objectFactory = new ObjectFactory<Object>() {
			@Override
			public Object getObject() throws BeansException {
				return object;
			}
		};
		return objectFactory;
	}

}

