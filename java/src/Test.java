
import be.vib.bits.QExecutor;
import be.vib.bits.QFunction;
import be.vib.bits.QHost;
import be.vib.bits.QMethod;
import be.vib.bits.QRange;
import be.vib.bits.QType;
import be.vib.bits.QTypeBuilder;
import be.vib.bits.QValue;

public class Test {
	
	final static String quasarInstallationFolder = "E:\\Program Files\\Quasar\\";
	
	static
	{		
		System.loadLibrary("JavaQuasarBridge"); // loads JavaQuasarBridge.dll (on Windows)
		System.out.println("JavaQuasarBridge loaded.");
	}

	public static void main(String[] args)
	{
		// We're using the QExecutor to run all bridge code on a the same single thread.
		// But: in the example here, without the executor all code would execute on a single thread already (the main thread) so this is not really needed in this case.
		// However: It serves as an example for user interface programs where some bridge code would get called from the main thread,
		// and some code from the Java Event Dispatching thread. In that case the programmer must use QExecutor so all
		// Quasar code gets executed on a single thread. Also, we're toying with the idea of the bridge running some tasks via QExecutor
		// as well, for example finalizer code that deletes Quasar objects. Finalizer code is run from an arbitrary thread but must
		// use the Quasar thread. QExecutor would take care of that.
//		QExecutor.getInstance().submit(() -> {
			
			boolean loadCompiler = true;
			QHost.init("cuda", loadCompiler);
			
//			testLoadSource();
//			
//			testLoadSourceFromString();
//			
//			testFunctionWithTooManyArgs();
//			
//			testSimpleQValues();
//			
//			testArrayQValue();
//
//			testFunction1();
//	
//			testFunction2();
			
//			testGaussian();  // leaks 1 matrix in GPU memory
//			
//			testCellArrayAccess(); // leaks 1 matrix in GPU memory
//			
//			testRange1();
			
			testArraySlicing();
			
//			testUserTypes();
	
			System.out.println("about to release");
			
			QHost.release();
//		});
			
			// FIXME: check for exceptions thrown inside the executor!! Currently they get silently lost!
		
		
		// Wait for tasks to complete and then stop the executor.
		// (Without it the executor will keep waiting for new tasks
		// and the program will not terminate.)
		QExecutor.getInstance().shutdown();
	}
	
	private static void testSimpleQValues()
	{
		final int answer = 42;
		final float pi = 3.14f;
		final String haiku = "furu ike ya / kawazu tobikomu / mizu no oto";
		final String nihongo = "\u65E5\u672C\u8A9E";
		
		QValue qv = new QValue();
		QValue qi = new QValue(answer);
		QValue qf = new QValue(pi);
		QValue qs1 = new QValue(haiku);
		QValue qs2 = new QValue(nihongo);
		
		assert(qi.getInt() == answer);		
		assert(qf.getFloat() == pi);
		assert(qs1.getString().equals(haiku));
		assert(qs2.getString().equals(nihongo));
		
		qv.delete();
		qi.delete();
		qf.delete();
		qs1.delete();
		qs2.delete();
	}
	
	private static void testArrayQValue()
	{
		float[] a = new float[10000];
		for (int i = 0; i < a.length; i++)
			a[i] = i;
		
		QValue q = new QValue(a);
	
		// Check size
		assert(q.size(0) == 1);
		assert(q.size(1) == 10000);
		assert(q.size(2) == 1);
		
		// Check content
	    assert(q.at(5000).getFloat() == 5000);
		
	    // Immediate manual cleanup - it's a "large" array.
		q.delete();
	}

	private static void testLoadSource()
	{
		// TODO: avoid dependency on files external to this project
		QHost.loadSourceModule(quasarInstallationFolder + "Library\\dwt.q");
		
		boolean exists = QHost.functionExists("dwt2d");
		assert(exists);
	}

	private static void testLoadSourceFromString()
	{
		String program = 
				"function [] = myprint(x)\n"
				+ "  print(x)\n"
				+ "end";
				
		QHost.loadModuleFromSource("mymodule", program);
				
		boolean exists = QHost.functionExists("myprint");
		assert(exists);
		
		System.out.println("myprint exists? " + (exists ? "yes" : "no"));
		
		boolean unloaded = QHost.unloadModule("mymodule");
		assert(unloaded);
	}
	
	private static void testFunction1()
	{
		QFunction tic = new QFunction("tic()");
		QFunction toc = new QFunction("toc()");
		QFunction imread = new QFunction("imread(string)");
		QFunction imshow = new QFunction("imshow(cube)");
		
		assert(QHost.functionExists("tic"));
		assert(QHost.functionExists("toc"));
		assert(QHost.functionExists("imread"));
		assert(QHost.functionExists("imshow"));

		// FIXME: what about the unused void return values of tic() and toc()? Check if and how they get deleted.
		tic.apply();
		QValue image = imread.apply(new QValue("lena_big.tif"));  // FIXME: check if/how the argument to 'apply' gets deleted
		toc.apply(); // prints the time taken to read the image
		
		assert(image.size(0) == 512);
		assert(image.size(1) == 512);
		assert(image.size(2) == 3);

		System.out.println("Java: dimensions of result from imread: " + image.size(0) + " x " + image.size(1) + " x " + image.size(2));
		
		imshow.apply(image);
		
		// Handle user interaction with the image window.
		// (Blocks until all image windows are closed.)
		QHost.runApp();
				
		// NOT SO GOOD
		//image.delete(); // TODO: if we do not do the delete, Quasar release will warn us that there still is an image on the GPU
		// (QValue.finalize is not called because the JVM did not create the QValue object, we created it on the C++ side.)
		
		// FIXME: delete it all - delete functions?
		
		image.delete();
	}

	private static void testFunction2()
	{
		// Build a Quasar array of floats
		float[] a = { 3.0f, -5.0f, 12.0f, -1.0f };
		QValue array = new QValue(a);
		
		// Print it
		QFunction print = new QFunction("print(...)");
		assert(QHost.functionExists("print"));
		
		print.apply(array);
		
		// Also calculate the maximum value in the array
		QFunction maximum = new QFunction("max(??)");
		assert(QHost.functionExists("max"));
		
		QValue maxVal = maximum.apply(array);
		assert(maxVal.getFloat() == 12.0f);
		
		// Let's calculate ln(e) via Quasar.
		QFunction ln = new QFunction("log(??)");
		assert(QHost.functionExists("log"));
		
		QValue e = new QValue(2.718281828f);  // more or less
		QValue one = ln.apply(e);
		assert(Math.abs(one.getFloat() - 1.0f) < 0.0001f);

		// Manually cleanup matrices in GPU memory. FIXME
		array.delete();
	}
	
	private static void testFunctionWithTooManyArgs()
	{
		QFunction f = new QFunction("print(...)");
		
		boolean exceptionThrown = false;
		try
		{
			// QFunction.apply() does not support more than 8 arguments and should throw an exception.
			f.apply(new QValue(1), new QValue(2), new QValue(3), new QValue(4),
					new QValue(5), new QValue(6), new QValue(7), new QValue(8),
					new QValue(9));
		}
		catch (java.lang.IllegalArgumentException e)
		{
			exceptionThrown = true;
		}
		
		assert(exceptionThrown);
	}
	
	private static void testGaussian()
	{
		// This Quasar fragment was borrowed from the sample program sample3_inlineprogram.cpp
		// which is part of the Quasar installation.
		String program =
				"function y = gaussian_filter(x, fc, n)\n"
				+ "	function [] = __kernel__ gaussian_filter_hor(x : cube, y : cube'unchecked, fc : vec'unchecked, n : int, pos : vec3)\n"
				+ "		sum = 0.\n"
				+ "		for i=0..numel(fc)-1\n"
		    	+ "			sum = sum + x[pos + [0,i-n,0]] * fc[i]\n"
				+ "		end\n"
				+ "		y[pos] = sum\n"
				+ "	end\n"
				+ "\n"
				+ "	function [] = __kernel__ gaussian_filter_ver(x : cube, y : cube'unchecked, fc : vec'unchecked, n : int, pos : vec3)\n"
				+ "		sum = 0.\n"
				+ "		for i=0..numel(fc)-1\n"
		    	+ "			sum = sum + x[pos + [i-n,0,0]] * fc[i]\n"
				+ "		end\n"
				+ "		y[pos] = sum\n"
				+ "	end\n"
				+ "\n"
				+ "	z = uninit(size(x))\n"
				+ "	y = uninit(size(x))\n"
				+ "	parallel_do (size(y), x, z, fc, n, gaussian_filter_hor)\n"
				+ "	parallel_do (size(y), z, y, fc, n, gaussian_filter_ver)\n"
				+ "end";
		
		QHost.loadModuleFromSource("example", program);   // TODO: check/ask what the meaning is of the moduleName in loadModuleFromSource
		
		boolean exists = QHost.functionExists("gaussian_filter");
		assert(exists);
		
		QFunction imread = new QFunction("imread(string)");
		QFunction imshow = new QFunction("imshow(cube)");
		QFunction filter = new QFunction("gaussian_filter(cube,vec,int)");
		
		QValue imageIn = imread.apply(new QValue("lena_big.tif"));
		System.out.println("Image dimensions: " + imageIn.size(0) + " x " + imageIn.size(1) + " x " + imageIn.size(2));

		float filterCoeff[] = { 1.0f / 9, 2.0f / 9, 3.0f / 9, 2.0f / 9, 1.0f / 9 };
		QValue imageOut = filter.apply(imageIn, new QValue(filterCoeff), new QValue(2));

		QValue p1 = imshow.apply(imageIn);
		QValue p2 = imshow.apply(imageOut);

		// We now "connect" the two imshow windows.
		// (Connecting them has the effect that zooming and panning
		// in one image window will do the same zoom/pan in the other.)
		QType t1 = new QType(p1);
		QMethod connect = new QMethod(t1, "connect(??)");
		connect.apply(p1, p2);  // Call the function p1.connect(p2)
		
		QHost.runApp();
		
		// Manually cleanup matrices in GPU memory. FIXME
		
		imageIn.delete();
		imageOut.delete();
	}
	
	private static void testCellArrayAccess()
	{		
		QHost.loadSourceModule(quasarInstallationFolder + "Library\\dtcwt.q");
		
		QValue selcw = QValue.readhostVariable("filtercoeff_selcw");  // filtercoeff_selcw is a 6 x 6 cell array		
		assert(selcw.size(0) == 6);
		assert(selcw.size(1) == 6);
		
		QValue coeffs = selcw.at(3, 1); // coeffs is now a 2 x 12 matrix of scalars
		assert(coeffs.size(0) == 2);
		assert(coeffs.size(1) == 12);

		QValue coeff = coeffs.at(0, 1); // coeff is a scalar
		assert(coeff.size(0) == 1);
		assert(coeff.size(1) == 1);

		assert(Math.abs(coeff.getFloat() - 0.0133588733151555f) < 0.0001f);
		
		// Manually cleanup matrices in GPU memory. FIXME
		selcw.delete();
		coeffs.delete();
		coeff.delete();
	}
	
	private static void testUserTypes()
	{
		// |This is work in progress.
		// TODO: Mimick the example e:\Program Files\Quasar\Interop_Samples\Cpp_API\sample7_usertypes.cpp
		
		// This works:
		QTypeBuilder builder = new QTypeBuilder("sample", "point");
		builder.addField("x", new QType("int"));
		builder.addField("y", new QType("int"));
	
		// FIXME This does not work. It seems the object() function cannot be found. Check with Bart.
		QFunction object = new QFunction("object()");		
		boolean exists = QHost.functionExists("object");
		System.out.println("object function exists? " + (exists ? "yes" : "no"));
		// assert(exists); // fails
	}
	
	private static void testRange1()
	{
		QFunction maximum = new QFunction("max(??)");

		// Some integer ranges
		QRange range1 = new QRange(1, 10, 1);  // [ 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 ]
		assert(maximum.apply(range1).getFloat() == 10.0f);  // Note: integer range became range of scalars
		assert(range1.size(1) == 10);
		
		QRange range2 = new QRange(1, 1, 10); // [ 1 ]
		assert(maximum.apply(range2).getFloat() == 1);  // Note: integer range became range of scalars
		assert(range2.size(1) == 1);
		
		QRange range3 = new QRange(1, 3, 10); //  [ 1 ]
		assert(maximum.apply(range3).getFloat() == 1);  // Note: integer range became range of scalars
		assert(range3.size(1) == 1);

		QRange range4 = new QRange(1, 10, 3); //  [ 1, 4, 7, 10 ]
		assert(maximum.apply(range4).getFloat() == 10);  // Note: integer range became range of scalars
		assert(range4.size(1) == 4);

		// A float range
		QRange range5 = new QRange(1.0f, 10.0f, 0.5f); // [ 1, 1.5, 2, 2.5, 3, 3.5, 4, 4.5, 5, 5.5, 6, 6.5, 7, 7.5, 8, 8.5, 9, 9.5, 10 ]
		assert(maximum.apply(range5).getFloat() == 10.0f);
		assert(range5.size(1) == 19);
		
		// Manually cleanup matrices in GPU memory. FIXME
		range1.delete();
		range2.delete();
		range3.delete();
		range4.delete();
		range5.delete();
		maximum.delete();
	}
	
	private static void testArraySlicing()
	{
		QFunction imread = new QFunction("imread(string)");
		assert(QHost.functionExists("imread"));

		QValue image = imread.apply(new QValue("lena_big.tif"));		
		assert(image.size(0) == 512);
		assert(image.size(1) == 512);
		assert(image.size(2) == 3);

		// Slice channel 2 from the image (= the blue channel)
		QValue blueChannel = image.at(new QRange(), new QRange(), new QValue(2));
		
		assert(blueChannel.size(0) == image.size(0));
		assert(blueChannel.size(1) == image.size(1));
		assert(blueChannel.size(2) == 1);
		
		assert(blueChannel.at(100, 200).getFloat() == image.at(100, 200, 2).getFloat());
		
		// Crop the image
		QValue face = image.at(new QRange(128, 383), new QRange(128, 383), new QValue());
		
		assert(face.size(0) == 256);
		assert(face.size(1) == 256);
		assert(face.size(2) == 3);

//		QFunction imshow = new QFunction("imshow(cube)");
//		imshow.apply(face);
//		QHost.runApp();
		
		// Manually cleanup matrices in GPU memory. FIXME
		image.delete();
		blueChannel.delete();
		face.delete();
	}
}