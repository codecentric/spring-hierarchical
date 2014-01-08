package de.codecentric.spring.hierarchical;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.FatalBeanException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;


@Component
@Scope(HierarchicalThreadScope.SCOPE_NAME)
public class HierarchicalContext implements CurrentLevelContext {
	
	private Map<String, Object> nameToObject = new HashMap<String, Object>();
	private HierarchicalContext parentContext;
	private ListableBeanFactory springContext;

	public HierarchicalContext(HierarchicalContext parentContext,
			ListableBeanFactory springContext) {
		this.parentContext = parentContext;
		this.springContext = springContext;
	}

	public Object get(String name) {
		if (isNameForThisClass(name)) {
			return this;
		}
		
		Object object = nameToObject.get(name);
		if (object != null) {
			return object;
		}
		boolean isRoot = parentContext == null;
		return isRoot ? null : parentContext.get(name);
	}

	public HierarchicalContext contextOfBean(String name){
		if (isNameForThisClass(name)) {
			return this;
		}
		Object object = nameToObject.get(name);
		if (object != null) {
			return this;
		}
		boolean isRoot = parentContext == null;
		return isRoot ? null : parentContext.contextOfBean(name);
	}

	private boolean isNameForThisClass(String name) {
		return springContext.isTypeMatch(name, CurrentLevelContext.class)
				|| springContext.isTypeMatch(name, HierarchicalContext.class);
	}

	public void put(String name, Object object) {
		nameToObject.put(name, object);
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T createBeanInSubContext(Class<T> typeOfBeanToCreate) {
		return (T) createBeanInSubContext(beanNameForType(typeOfBeanToCreate));
	}

	@Override
	public Object createBeanInSubContext(String name) {
		HierarchicalContext childContext = new HierarchicalContext(this,
				springContext);
		HierarchicalThreadScope.setCurrentThreadLocalContext(childContext);
		try {
			
			Object bean = springContext.getBean(name);
			// jetzt sollte die geforderte Bean + alle per @Autowired 
			// referenzierten Beans im Kontext sein.
			childContext.injectCircularAutowiredAnnotatedBeans();
			return bean; 
		} finally {
			// um subtile Hierarchie-Fehler zu vermeiden
			HierarchicalThreadScope.setCurrentThreadLocalContext(null);
		}
	}
	
	public<T> String beanNameForType(Class<T> typeOfBean){
		String[] names = springContext.getBeanNamesForType(typeOfBean);
		switch (names.length) {
		case 0:
			throw new BeanCreationException(
					"no bean registered for requested bean type "
							+ typeOfBean);
		case 1:
			return names[0];
		default:
			throw new BeanCreationException(names.length
					+ " beans registered for requested bean type "
					+ typeOfBean
					+ ". can only resolve by class if name is not ambiguous");
		}
	}

	private void injectCircularAutowiredAnnotatedBeans(){
		
		Map<String, Object> currentCandidates = new HashMap<String, Object>();
		
		// Copy current Map to avoid concurrent modifications 
		for(String beanName : nameToObject.keySet()){
			currentCandidates.put(beanName, nameToObject.get(beanName));
			
		}
		
		for(String beanName : currentCandidates.keySet()){
			Object bean = nameToObject.get(beanName);
			injectAnnotatedFields(bean);
			injectAnnotatedSetters(bean);
		}
	}

	private void injectAnnotatedSetters(Object bean) {
		Class<?> typeOfBean = bean.getClass();
		Method[] methods = typeOfBean.getDeclaredMethods();
		for(Method method : methods){
			if(method.isAnnotationPresent(CircularAutowired.class)){
				Class<?>[] types = method.getParameterTypes();
				switch (types.length) {
				case 0:
					throw new FatalBeanException(
							"at least one parameter is required for method "
									+ method.getName()
									+ " annotated as @"+CircularAutowired.class.getSimpleName()
									+ " in class " + bean.getClass());
				case 1:
					Object value = getInstanceForCircularAutowiring(bean, types[0]);
					ReflectionUtils.makeAccessible(method);
					ReflectionUtils.invokeMethod(method, bean, value);
					break;
				default:
					throw new FatalBeanException(
							"setter method "+method.getName()
									+"  annotated as @"+ CircularAutowired.class.getSimpleName()
									+ " in class " + bean.getClass()
									+ " requires "+types.length + " parameters "
									+ " but should require only one parameter. ");
				}
			}
		}
	}


	private void injectAnnotatedFields(Object bean) {
		Class<?> typeOfBean = bean.getClass();
		Field[] fields = typeOfBean.getDeclaredFields();
		for(Field field : fields){
			if(field.isAnnotationPresent(CircularAutowired.class)){
				Object value = getInstanceForCircularAutowiring(bean, field.getType());
				ReflectionUtils.makeAccessible(field);
				ReflectionUtils.setField(field, bean, value);
			}
		}
	}
	
	
	
	private<T> Object getInstanceForCircularAutowiring(Object bean, Class<T> typeForCircularAutowiring){
		
		
		String name = beanNameForType(typeForCircularAutowiring);
		Object beanForCirularAutowiring = get(name);
		
		if(beanForCirularAutowiring == null){
			beanForCirularAutowiring = springContext.getBean(name);
		}
		
		
		performCircularAutowiringOnNestedCandidates(bean, typeForCircularAutowiring,
				beanForCirularAutowiring);
		
		
		return beanForCirularAutowiring;
		
	}


	private <T> void performCircularAutowiringOnNestedCandidates(Object bean,
			Class<T> typeForCircularAutowiring, Object beanForCirularAutowiring) {
		
		
		boolean fieldInjected = circularInjectAnnotatedField(bean, beanForCirularAutowiring);
				
		
		boolean setterInjected = circularInjectAnnotatedSetters(bean,
				typeForCircularAutowiring, beanForCirularAutowiring);
		
		if(!fieldInjected && !setterInjected){
			throw new BeanCreationException(" element with type "
					+ typeForCircularAutowiring.getName()
					+ " in " + bean.getClass().getName()
					+ " annotated as @" + CircularAutowired.class.getSimpleName()
					+ " but no backreference annotated as @" + CircularAutowired.class.getSimpleName()
					+ " was found in " +typeForCircularAutowiring.getName());
		}
	}

	private <T> boolean circularInjectAnnotatedField(Object bean, Object beanForCirularAutowiring) {
		boolean fieldInjected = false;
		
		Field targetFieldForCircularAutowiring = null;
		
		for(Field field : beanForCirularAutowiring.getClass().getDeclaredFields()){
			if(field.isAnnotationPresent(CircularAutowired.class) 
					&& isInstanceOfType(field.getType(), bean)){
				targetFieldForCircularAutowiring = field;
				break;
			}
		}
		
		if(targetFieldForCircularAutowiring != null){
			ReflectionUtils.makeAccessible(targetFieldForCircularAutowiring);
			ReflectionUtils.setField(targetFieldForCircularAutowiring, beanForCirularAutowiring, bean);
			fieldInjected = true;
		}
		return fieldInjected;
	}

	private <T> boolean circularInjectAnnotatedSetters(Object bean,
			Class<T> typeForCircularAutowiring, Object beanForCirularAutowiring) {
		Method[] methods =  beanForCirularAutowiring.getClass().getDeclaredMethods();
		boolean methodFound = false;
		for(Method method : methods){
			if(method.isAnnotationPresent(CircularAutowired.class)){
				Class<?>[] types = method.getParameterTypes();
				switch (types.length) {
				case 0:
					throw new FatalBeanException(
							"at least one parameter is required for method "
									+ method.getName()
									+ " annotated as @"+CircularAutowired.class.getSimpleName()
									+ " in class " + typeForCircularAutowiring.getName());
				case 1:
					Class<?> paramType = types[0];
					if(isInstanceOfType(paramType, bean)){
						methodFound = true;
						ReflectionUtils.makeAccessible(method);
						ReflectionUtils.invokeMethod(method, beanForCirularAutowiring, bean);
					}
					
					break;
				default:
					throw new FatalBeanException(
							"setter method "+method.getName()
									+"  annotated as @"+ CircularAutowired.class.getSimpleName()
									+ " in class " + bean.getClass()
									+ " requires "+types.length + " parameters "
									+ " but should require only one parameter. ");
				}
			}
		}
		return methodFound;
	}
	
	public boolean isInstanceOfType(Class<?> type, Object instance){
		if(type.equals(instance.getClass())){
			return true;
		}
		
		for(Class<?> currentInterface : instance.getClass().getInterfaces()){
			if(currentInterface.equals(type)){
				return true;
			}
		}
		
		return false;
	}
}
