Provides a custom Ivy resolver for declaring dependencies on ATG modules. The 
modules must be available locally and are resolved via the `DYNAMO_HOME` 
environment variable.

## Declaring Dependencies

	// always use provided scope since the modules will be provided by ATG
	provided group:'ATG_MODULE', name:'DAS', version:'SNAPSHOT'

Module dependencies use the group `ATG_MODULE`. 
The artifact name is the name of the module,e.g., `DAS`.  The version should be `SNAPSHOT` so modules can be updated.

If you are developing an ATG module, your module can declare a dependency on itself. e.g.,

	provided group:'ATG_MODULE', name:'MYMODULE', version:'SNAPSHOT'

The resolver will automatically pick up any other modules your module depends 
on in its MANIFEST.MF, so you don't have to declare dependencies twice.

## Build Systems

Any build system that utilizes Ivy will work, but here are some common examples:

### Gradle

Because we are loading a custom resolver, the atg-resolver jar cannot
be a dependency. That makes it a little tricky to use.

If you can add the atg-resolver jar to your Gradle classpath, then you
can just add this to the `repositories` section:

	add com.noahsloan.atg.ivy.AtgModuleRepository.newResolver

If not, then you should add the jar to your project manually and add this:

	def myCL = new URLClassLoader([new File(
				"atg-resolver-1.0-SNAPSHOT.jar"
			).toURI().toURL()] as URL[],
			org.apache.ivy.plugins.repository.AbstractRepository.classLoader)
	add myCL.loadClass('com.noahsloan.atg.ivy.AtgModuleRepository').newResolver

This creates a class loader to load the required classes directly from the jar.

### Grails

Use the Grails [plugin][atg-grails-plugin] and declare dependencies as above.

If you can't use the plugin for some reason, the configuration is almost the same as Gradle.
Inside `grails.project.dependency.resolution` add the snippet from the Gradle section but change
`add` to `resolver` and the file path to `${basedir}/lib/atg-resolver-1.0-SNAPSHOT.jar`.

See the plugin for an [example][atg-grails-build-config].

### Java

Creating the resolver is simple.

	DependencyResolver resolver = com.noahsloan.atg.ivy.AtgModuleRepository.getNewResolver();

What you do with it is beyond the scope of this file right now.

[atg-grails-plugin]: https://github.com/iamnoah/grails-atg-core-plugin "It's awesome."
[atg-grails-build-config]: https://github.com/iamnoah/grails-atg-core-plugin/blob/master/grails-app/conf/BuildConfig.groovy#L17
