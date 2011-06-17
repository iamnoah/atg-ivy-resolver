package com.noahsloan.atg.ivy

import org.apache.ivy.plugins.repository.AbstractRepository;
import org.apache.ivy.plugins.repository.BasicResource;
import org.apache.ivy.plugins.repository.Resource;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.apache.ivy.plugins.resolver.RepositoryResolver;
import org.apache.ivy.plugins.resolver.packager.BuiltFileResource;
import org.apache.tools.ant.taskdefs.Jar;
import org.apache.tools.ant.taskdefs.Manifest;

/**
 * Respository implementation that resolves ATG modules as dependencies.
 * The jar file resolved will be the module's classes.jar file.
 * Module manifests are parsed to resolve dependencies on other modules.
 */
class AtgModuleRepository extends AbstractRepository {
	
	final ORG = 'ATG_MODULE'
	
	File atg_root
	
	File default_jar
	
	File getAtgRoot() {
		atg_root = atg_root ?: resolveAtgRoot() ?: new File(".").absoluteFile
	}
	
	File getDefaultJar() {
		default_jar = default_jar ?: createDefaultJar()
	}
	
	def resources = [:]
	
	protected File createDefaultJar() {
		Jar task = new Jar()
		def defaultManifest = File.createTempFile("atg-default",".mf")
		Manifest.defaultManifest.print(defaultManifest.newPrintWriter())
		task.manifest = defaultManifest
		def defaultJar = File.createTempFile("atg-default",".jar")
		task.destFile = defaultJar
		task.execute()
		defaultJar
	}
	
	protected File resolveAtgRoot() {
		def root = System.getenv("DYNAMO_HOME") ?: System.getenv("ATG_HOME")
		if(root) {
			root = new File(root).parentFile
		}  else {
			root = new File(".").absoluteFile
			while(root && !new File(root,"home/bin/checkDynamo").exists() ) {
				root = root.parentFile
			}
		}
		if(!root) {
			throw new Exception("Could not resolve ATG Root. You need to set DYNAMO_HOME or ATG_HOME")
		}

		println "ATG root dir is ${root.absolutePath}"
		root
	}
	
	@Override
	public void get(String src, File dest) throws IOException {
		def match = src =~ /atg-(.+?)-.*-ivy\.xml/
		def r 
		if(match) {
			r = getResource(match[0][1])
		} else {
			match = src =~ /([^\/]+)\/lib\/classes\.jar$/
			if(match) {
				r = getResource("${ORG}:${match[0][1]}:any:jar")
			} else {
				r = getResource('default-jar')
			}
		}
		fireTransferInitiated(r,5)
		fireTransferStarted()
		
		new URL(src).withInputStream { ins ->
			dest.withOutputStream { out -> out << ins }
		}
		fireTransferProgress(dest.size())
		fireTransferCompleted(dest.size())
	}
	
	@Override
	public Resource getResource(String src) throws IOException {
		if(resources[src]) {
			return resources[src]
		}
		if(src == 'default-jar') {
			return new BuiltFileResource(defaultJar)
		}
		def (org,module,ver,type) = (src.split(/:/) as List)
		if(org != ORG) {
			return new BasicResource('not found',false,0,0,false)
		}
		File file = new File(atgRoot,"${module}/META-INF/MANIFEST.MF")
		if(file.exists() && type == "ivy") {
			File tmp = File.createTempFile("atg-${src}-","-ivy.xml")
			tmp.withWriter {
				def build = new groovy.xml.MarkupBuilder(it)
				build.doubleQuotes = true
				build.'ivy-module'(version:'2.0') {
					info(
							organisation: org,
							module: module,
							revision: ver,
							status: 'release',
							'default': 'true',
							)
					build.configurations() {
						conf(name:"default",visibility:"public")
					}
					build.publications() {
						artifact(name:module,type:"jar",ext:"jar",conf:"default")
					}
					build.dependencies {
						file.withReader {
							Manifest m = new Manifest(it)
							def val = m.getMainSection().getAttributeValue("ATG-Required")
							val?.split(/\s+/)?.findAll { it }?.each {
								build.dependency(org:ORG,name:it,rev:ver)
							}
						}
					}
				}
				it.close()
			}
			resources[src] = new BuiltFileResource(tmp)
		} else if(file.exists()){
			File jar = new File(atgRoot,"${module}/lib/classes.jar")
			if(jar.exists()) {
				new BuiltFileResource(jar)
			} else {
				new BuiltFileResource(defaultJar)
			}
		}  else {
			new BuiltFileResource(file)
		}
	}
	
	@Override
	public List<?> list(String parent) throws IOException {
		return Collections.emptyList();
	}
	
	/**
	 * Factory method for creating a resolver using an instance of the repository.
	 * 
	 * @param name the resolver name. Default is "ATG Resolver"
	 * @return a configured RepositoryResolver.
	 */
	public static RepositoryResolver getNewResolver(name = "ATG Resolver") {
		def repo = new AtgModuleRepository()
		def atgResolver = new RepositoryResolver()
		atgResolver.repository = repo
		atgResolver.addIvyPattern("[organisation]:[module]:[revision]:[type]:[artifact]:[ext]")
		atgResolver.addArtifactPattern("[organisation]:[module]:[revision]:[type]:[artifact]:[ext]")
		atgResolver.name = name		
		atgResolver
	}
}