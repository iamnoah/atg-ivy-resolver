Provides a custom Ivy resolver for declaring dependencies on ATG modules. The 
modules must be available locally and are resolved via the `ATG_${ver}_ROOT` 
environment variables, where `${ver}` is the version of ATG required.
This version of the resolver allows you to require a specific version of 
ATG as a dependency. So point `ATG_10.2_ROOT` at `c:\ATG\ATG10.2` for example, 
`ATG_10.1.2_ROOT` at `c:\ATG\ATG10.1.2`, etc.
If your custom modules are not in the root of your ATG installation, then
use the `CUSTOM_ROOT` environment to point to the parent directory of your 
custom modules. 

## Declaring Dependencies

	// always use provided scope since the modules will be provided by ATG
	provided group:'ATG_MODULE', name:'DAS', version:'10.2'

Module dependencies use the group `ATG_MODULE`. 
The artifact name is the name of the module, e.g., `DAS`. 
Nested modules, like `DCS.CustomCatalogs` and `DAF.Endeca.Base` are supported.
The version should be the version of ATG you want to compile to.

Custom ATG modules should declare a dependency on themselves. e.g.,

	provided group:'ATG_MODULE', name:'Store.EStore', version:'10.2'

In the above example, the resolver looks for the provided module name in the 
locations specified using the environment variables `ATG_10.2_ROOT` and 
`CUSTOM_ROOT`. It will then recursively parse through the MANIFEST.MF file 
of each module that is a dependency, so you don't have to declare dependencies 
in multiple places.

The following MANIFEST.MF attributes are used to resolve dependencies:
* ATG-Required - Used by the ATG assembler to pull in required modules.
* ATG-Required-If-Present - Used but not mandated by the ATG assembler.
* ATG-Required-To-Compile - Not used by ATG. Use it to specify a compile time dependency only. This is an alternative to the above examples.

## Build Systems

Any build system that utilizes Ivy will work, but here are some common examples:

### Gradle

You can use a [`buildscript`][gradle-external-deps] section to download the resolver jar so it can be 
added as a repository in your main script:

	buildscript {
	    dependencies {
	        classpath files('/path/to/this.jar')
	    }
	}

	allprojects { // see http://gradle.org/current/docs/userguide/userguide_single.html#sec:subproject_configuration
		repositories {
			add com.noahsloan.atg.ivy.AtgModuleRepository.newResolver
		}

		dependencies {
		    compile group:"ATG_MODULE", name:pathToAtgModuleName(project.path), version:"10.1.2"
		}
		
		// the rest of your tasks go here
	}

    def pathToAtgModuleName(String path) { 
        path[1..-1].replaceAll(':','.')
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
