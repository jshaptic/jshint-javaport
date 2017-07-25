package org.jshint.test.unit;

import java.util.ArrayList;
import java.util.List;

import org.jshint.JSHint;
import org.jshint.JSHintException;
import org.jshint.LexerEventListener;
import org.jshint.utils.JSHintModule;
import org.jshint.utils.JSHintUtils;
import org.jshint.utils.EventContext;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * The JSHint API does not allow for un-registering "modules", and the Nodeunit
 * API does not support per-group setup/teardown logic. These deficiencies
 * necessitate additional logic in the test suite in order to test the JSHint
 * "module" API hygienically:
 *
 * - Only one JSHint "module" should be added at any time, regardless of the
 *   number of tests executed
 * - No JSHint "module" should run following the completion of this group of
 *   tests
 */
public class TestModuleApi extends Assert
{
	@BeforeClass
	private void setupBeforeClass()
	{
		JSHintUtils.reset();
	}
	
	@Test
	public void testIdentifiers()
	{
		JSHint jshint = new JSHint();
		
		String[] src = {
			"var x = {",
		    "  y: 23,",
		    "  'z': 45",
		    "};"
		};
		
		EventContext context;
		List<EventContext> expected = new ArrayList<EventContext>();
		
		context = new EventContext();
		context.setLine(1);
		context.setCharacter(6);
		context.setFrom(5);
		context.setName("x");
		context.setRawName("x");
		context.setProperty(false);
		expected.add(context);
		
		context = new EventContext();
		context.setLine(2);
		context.setCharacter(4);
		context.setFrom(3);
		context.setName("y");
		context.setRawName("y");
		context.setProperty(false);
		expected.add(context);
		
		final List<EventContext> actual = new ArrayList<EventContext>();
		jshint.addModule(new JSHintModule()
			{
				@Override
				public void execute(JSHint linter)
				{
					linter.on("Identifier", new LexerEventListener()
						{
							@Override
							public void accept(EventContext x) throws JSHintException
							{
								actual.add(x);
							}
						});
				}
			});
		
		jshint.lint(src);
		
		assertEquals(actual, expected);
	}
}