NOTE: I no longer work with ATG, so I can't maintain this module. Try [this fork](https://github.com/rajeshja/atg-ivy-resolver).

Provides a custom Ivy resolver for declaring dependencies on ATG modules. The 
modules must be available locally and are resolved via the `DYNAMO_HOME` 
environment variable.

## Declaring Dependencies

	// always use provided scope since the modules will be provided by ATG
	provided group:'ATG_MODULE', name:'DAS', version:'SNAPSHOT'

Module dependencies use the group `ATG_MODULE`. 
The artifact name is the name of the module,e.g., `DAS`. 
The version should be `SNAPSHOT` so modules can be updated.

If you are developing an ATG module, your module can declare a dependency on itself. e.g.,

	provided group:'ATG_MODULE', name:'MYMODULE', version:'SNAPSHOT'

The resolver will automatically pick up any other modules your module depends 
on in its MANIFEST.MF, so you don't have to declare dependencies twice.

## Build Systems

Any build system that utilizes Ivy will work, but here are some common examples:

### Gradle

You can use a [`buildscript`][gradle-external-deps] section to download the resolver jar so it can be 
added as a repository in your main script:

	buildscript {
	    repositories {
	        mavenCentral()
	    }
	    dependencies {
	        classpath 'com.noahsloan.atg:atg-resolver:1.0'
	    }
	}

	allprojects { // see http://gradle.org/current/docs/userguide/userguide_single.html#sec:subproject_configuration
		repositories {
			add com.noahsloan.atg.ivy.AtgModuleRepository.newResolver
		}

		dependencies {
		    compile group:"ATG_MODULE", name:"DAS",version:"SNAPSHOT"
		}
		
		// the rest of your tasks go here
	}

### Grails

Use the Grails [plugin][atg-grails-plugin] and declare dependencies as above.

If you can't use the plugin for some reason, then you can add this to 
grails-app/conf/BuildConfig.groovy:

	@Grab(group='com.noahsloan.atg',module="atg-resolver",version="1.0")
	import com.noahsloan.atg.ivy.AtgModuleRepository

	grails.project.dependency.resolution = {
		resolver AtgModuleRepository.newResolver		
		// ... 
	}

See the plugin for an [example][atg-grails-build-config].

### Java

Creating the resolver is simple.

	DependencyResolver resolver = com.noahsloan.atg.ivy.AtgModuleRepository.getNewResolver();

What you do with it is beyond the scope of this file right now.

[atg-grails-plugin]: https://github.com/iamnoah/grails-atg-core-plugin "It's awesome."
[atg-grails-build-config]: https://github.com/iamnoah/grails-atg-core-plugin/blob/master/grails-app/conf/BuildConfig.groovy#L17
[gradle-external-deps]: http://gradle.org/current/docs/userguide/userguide_single.html#sec:external_dependencies
