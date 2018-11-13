package org.jshint.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.github.jshaptic.js4j.ContainerFactory;
import com.github.jshaptic.js4j.UniversalContainer;
import com.github.jshaptic.js4j.ValueCustomizer;

/**
 * Partial java port of the javascript's utility library <a href="https://lodash.com">Lodash</a>. This class consists
 * exclusively of static methods that operate on {@link UniversalContainer} containers.
 * 
 * Based on javascript library lodash@4.17.10 [https://lodash.com/license]
 */
public class Lodash
{
	private Lodash() {}
	
	public static boolean isArray(Object value)
	{
		return value != null && value instanceof UniversalContainer ? ((UniversalContainer)value).isArray() : (value instanceof List || value instanceof Set || value instanceof Object[]);
	}
	
	public static boolean isObject(Object value)
	{
		return value != null && value instanceof UniversalContainer ? !((UniversalContainer)value).isNull() && ((UniversalContainer)value).isObject() : (value instanceof Map || value instanceof List || value instanceof Set);
	}
	
	/**
	 * Assigns own enumerable properties of source OBJECT container(s) to the destination OBJECT container. Subsequent
	 * sources overwrite property assignments of previous sources.
	 * <p>
	 * Note: This method mutates destination OBJECT container.
	 * </p>
	 * 
	 * @param	object	destination container
	 * @param 	sources	source containers, which will override properties in the destination container
	 * @return	destination container with the overriden properties
	 */
	public static UniversalContainer extend(UniversalContainer object, UniversalContainer... sources)
	{
		if (object == null) return ContainerFactory.undefinedContainer(); 
		if (sources == null || !isObject(object)) return object;
		
		for (UniversalContainer source : sources)
		{
			if (source != null && source.test())
			{
				for (String key : source.keys())
				{
					object.set(key, source.get(key));
				}
			}
		}
		
		return object;
	}
	
	/**
	 * Recursively merges own enumerable properties of the source OBJECT container(s), that don't resolve to UNDEFINED
	 * into the destination OBJECT container. Subsequent sources overwrite property assignments of previous sources. If
	 * customizer is provided it's invoked to produce the merged values of the destination and source properties. If
	 * customizer returns UNDEFINED container merging is handled by the method instead. The customizer is invoked with
	 * two arguments: (objectValue, sourceValue).
	 * <p>
	 * Note: This method mutates destination OBJECT container.
	 * </p>
	 * 
	 * @param 	object destination container
	 * @param 	customizer instance of {@link ValueCustomizer} class with a custom logic, which will be aplied to every pair
	 * @param 	sources source containers, which properties will be merged into the destination container
	 * @return	destination container with the merged properties
	 */
	public static UniversalContainer merge(UniversalContainer object, ValueCustomizer customizer, UniversalContainer... sources)
	{
		if (object == null) return ContainerFactory.undefinedContainer();
		if (sources == null || !isObject(object)) return object;
		
		for (UniversalContainer source : sources)
		{
			if (source != null && source.test())
			{
				baseMerge(object, source, customizer, null, null);
			}
		}
		
		return object;
	}
	
	private static UniversalContainer baseMerge(UniversalContainer object, UniversalContainer source, ValueCustomizer customizer, List<UniversalContainer> stackA, List<UniversalContainer> stackB)
	{
		boolean isSourceArray = isArray(source);
		UniversalContainer elements = (isSourceArray ? source : new UniversalContainer(source.keys()));
		int index = -1;
		
		for (UniversalContainer e : elements)
		{
			String key = (isSourceArray ? String.valueOf(++index) : e.asString());
			UniversalContainer sourceValue = (isSourceArray ? e : source.get(key));
			
			if (isObject(sourceValue))
			{
				stackA = (stackA != null ? stackA : new ArrayList<UniversalContainer>());
				stackB = (stackB != null ? stackB : new ArrayList<UniversalContainer>());
				baseMergeDeep(object, source, key, customizer, stackA, stackB);
			}
			else
			{
				UniversalContainer objectValue = object.get(key);
				UniversalContainer result = (customizer != null ? customizer.customize(objectValue, sourceValue) : ContainerFactory.undefinedContainer());
				boolean isCommon = result.isUndefined();
				
				if (isCommon)
				{
					result = sourceValue;
				}
				if ((!result.isUndefined() || (isSourceArray && !object.has(key, false))) && (isCommon || !result.equals(objectValue)))
				{
					object.set(key, result);
				}
			}
		}
		
		return object;
	}
	
	private static void baseMergeDeep(UniversalContainer object, UniversalContainer source, String key, ValueCustomizer customizer, List<UniversalContainer> stackA, List<UniversalContainer> stackB)
	{
		int length = stackA.size();
		UniversalContainer sourceValue = source.get(key);
		
		while(length-- > 0)
		{
			if (stackA.get(length) == sourceValue)
			{
				object.set(key, stackB.get(length));
				return;
			}
		}
		
		UniversalContainer objectValue = object.get(key);
		UniversalContainer result = (customizer != null ? customizer.customize(objectValue, sourceValue) : ContainerFactory.undefinedContainer());
		boolean isCommon = result.isUndefined();
		
		if (isCommon)
		{
			result = sourceValue;
			if (isArray(sourceValue))
			{
				result = (isArray(objectValue) ? objectValue : ContainerFactory.createArray());
			}
			else if (isObject(sourceValue))
			{
				result = (isObject(objectValue) ? objectValue : ContainerFactory.createObject());
			}
			else
			{
				isCommon = false;
			}
		}
		
		stackA.add(sourceValue);
		stackB.add(result);
		
		if (isCommon)
		{
			object.set(key, baseMerge(result, sourceValue, customizer, stackA, stackB));
		}
		else if (!result.equals(objectValue))
		{
			object.set(key, result);
		}
	}
}