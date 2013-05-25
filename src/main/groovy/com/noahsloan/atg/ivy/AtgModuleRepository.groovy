package com.noahsloan.atg.ivy

import org.apache.ivy.plugins.repository.AbstractRepository;
import org.apache.ivy.plugins.repository.BasicResource;
import org.apache.ivy.plugins.repository.Resource;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.apache.ivy.plugins.resolver.RepositoryResolver;
import org.apache.ivy.plugins.resolver.packager.BuiltFileResource;
import org.apache.tools.ant.taskdefs.Jar;
import org.apache.tools.ant.taskdefs.Manifest;

import groovy.util.logging.Slf4j

/**
 * Respository implementation that resolves ATG modules as dependencies.
 * The jar file resolved will be the module's classes.jar file.
 * Module manifests are parsed to resolve dependencies on other modules.
 */
@Slf4j
class AtgModuleRepository extends AbstractRepository {
	
	final ORG = 'ATG_MODULE'

	final ATG_OTHER_PRODUCTS = ["CAF", "CSC-UI", "CSC", "Service-UI", "Service" ]
	
	File atg_root
	def atg_versioned_roots = [:]

	File custom_root
	
	File default_jar
	
	File getAtgRoot() {
		atg_root = atg_root ?: resolveAtgRoot() ?: new File(".").absoluteFile
	}

	File getAtgVersionedRoot(String version) { 
		atg_versioned_roots[version] = atg_versioned_roots[version] ?: resolveVersionedAtgRoot(version)
	}

	File getCustomRoot() {
		custom_root = custom_root ?: resolveCustomRoot()
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

		defaultJar.deleteOnExit()
		defaultManifest.deleteOnExit()

		defaultJar
	}
	
	protected File resolveAtgRoot() {
		def root = System.getenv("DYNAMO_HOME") ?: System.getenv("ATG_HOME")
		if(root) {
			root = new File(root).parentFile
		}  else {
			root = new File(".").absoluteFile
			while(root && !new File(root,"home/bin/checkDynamo").exists()) {
				root = root.parentFile
			}
		}
		if(!root) {
			throw new Exception("Could not resolve ATG Root. You need to set DYNAMO_HOME or ATG_HOME")
		}

		println "ATG root dir is ${root.absolutePath}"
		root
	}

	protected File resolveVersionedAtgRoot(String version) {
		def root = System.getenv("ATG_${version}_ROOT")
		if(root) {
			root = new File(root).absoluteFile
		}

		if(!root) {
			throw new Exception("Could not resolve ATG Root for version ${version}. Please set ATG_${version}_ROOT to a valid ATG installation.")
		}

		println "ATG ${version} root directory is ${root.absolutePath}"
		root
	}

	protected File resolveCustomRoot() {
		def root = System.getenv("CUSTOM_ROOT")
		if (root) {
			root = new File(root).absoluteFile
		} else { 
			root = new File(".").absoluteFile
		}
		root
	}
	
	@Override
	public void get(String src, File dest) throws IOException {
		def r 
		if (src.startsWith("file:/")) {
			def file = new File(src[6..-1])
			if (file.exists()) {
				r = new BuiltFileResource(file.absoluteFile)
			}
		}

		//Is this block required?
		if (!r) {
			def match = src =~ /atg-(.+?)-TMP-.*-ivy\.xml/
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
		def (org,module,ver,type,resName,resExt) = (src.split(/:/) as List)
		def modulePath = (module==null)?'tmp':module.replaceAll('\\.','/')
		if(org != ORG) {
			return new BasicResource(org + ' not found',false,0,0,false)
		}
		File file = getVersionedFile(ver, modulePath, "META-INF/MANIFEST.MF")

		if (!file.exists()) {
			throw new Exception("Couldn't find module ${module}. Is CUSTOM_ROOT set properly?")
		} else if (type == "ivy") { 
			File tmp = File.createTempFile("atg-${module}-TMP-","-ivy.xml")
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

					file.withReader {
						Manifest m = new Manifest(it)

						build.publications() {
							def classPath = m.getMainSection().getAttributeValue("ATG-Class-Path")

							classPath?.split(/\s+/)?.findAll{it}?.each {
								def pathName = it.split(/\./)[0..-2].join('.');
								def pathExt = it.split(/\./)[-1]
								artifact(name:pathName, type:pathExt, ext:pathExt, conf:"default")
							}
						}

						build.dependencies {

							def req = m.getMainSection().getAttributeValue("ATG-Required");
							def reqIfPresent = m.getMainSection().getAttributeValue("ATG-Required-If-Present")
							def reqToCompile = m.getMainSection().getAttributeValue("ATG-Required-To-Compile")

							req = (reqIfPresent==null) ? req : (req + ' ' + reqIfPresent)
							req = (reqToCompile==null) ? req : (req + ' ' + reqToCompile)
							req?.split(/\s+/)?.findAll {it}?.each {
								build.dependency(org:ORG,name:it,rev:ver)
							}
						}
					}
				}
				it.close()
			}
			tmp.deleteOnExit()
			
			resources[src] = new BuiltFileResource(tmp)
		} else {

			File jar = getVersionedFile(ver, modulePath, "${resName}.${resExt}")

			if(jar.exists()) {
				new BuiltFileResource(jar)
			} else {
				new BuiltFileResource(defaultJar)
			}
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

	private File getVersionedFile(String ver, String modulePath, String filePath) {
		File file = new File(getAtgVersionedRoot(ver),"${modulePath}/${filePath}")
		def int i=0
		while (!file.exists() && i<ATG_OTHER_PRODUCTS.size()) {
			file = new File(getAtgVersionedRoot(ver),"${ATG_OTHER_PRODUCTS[i]}${ver}/${modulePath}/${filePath}")
			i++
		}

		if(!file.exists()) {
			file = new File(customRoot,"${modulePath}/${filePath}")
		}

		file
	}
  
}