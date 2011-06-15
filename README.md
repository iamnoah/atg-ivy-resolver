## Usage

### Grails

Use the Grails [plugin][atg-grails-plugin]!

Grails' BuildConfig.groovy is compiled before dependencies are loaded, so including the resolver is a little tricky.
Put the jar in your lib directory and add this:

	grails.project.dependency.resolution = {
		def myCL = new URLClassLoader([new File(
					"${basedir}/lib/atg-resolver-1.0-SNAPSHOT.jar"
				).toURI().toURL()] as URL[],
				org.apache.ivy.plugins.repository.AbstractRepository.classLoader)
		resolver myCL.loadClass('com.noahsloan.atg.ivy.AtgModuleRepository').newResolver
	}

This creates a class loader to load the required classes directly from the jar.

### Java

Creating the resolver is simple.

	DependencyResolver resolver = com.noahsloan.atg.ivy.AtgModuleRepository.getNewResolver();

What you do with it is beyond the scope of this file right now.

[atg-grails-plugin]: https://github.com/iamnoah/grails-atg-core-plugin "It's awesome."
