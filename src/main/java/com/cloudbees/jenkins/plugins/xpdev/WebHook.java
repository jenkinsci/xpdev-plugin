package com.cloudbees.jenkins.plugins.xpdev;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.UnprotectedRootAction;
import hudson.plugins.git.GitSCM;
import hudson.plugins.mercurial.MercurialSCM;
import hudson.scm.SCM;
import hudson.scm.SubversionSCM;
import hudson.security.ACL;
import hudson.util.IOUtils;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.acegisecurity.Authentication;
import org.acegisecurity.context.SecurityContextHolder;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.io.StringReader;
import java.util.logging.Logger;

/**
 * @author: <a hef="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 * @see {http://docs.xp-dev.com/user-guide/webhooks.html}
 */
@Extension
public class WebHook implements UnprotectedRootAction {

        public String getIconFileName() {
            return null;
        }

        public String getDisplayName() {
            return null;
        }

        public String getUrlName() {
            return "xpdev-webhook";
        }

        /*

       {
          "repository":Name of the repository (Type:String),
          "message": Commit log message (Type:String),
          "timestamp": Timestamp of commit (Unix epoch in miliseconds) (Type:long),
          "author": Committer (Type:String),
          "revision": Commit revision (Type:long),

          "repository_ssl_path": Full SSL URL to the repository root (Type:String),
          "repository_path": Full non-SSL URL to the repository root (Type:String),

          "revision_web": Url to view the changeset on XP-Dev.com (Type:String),

          "added":List of paths that have been added (Type:String array),
          "removed":List of paths that have been removed/deleted (Type:String array),
          "replaced":List of paths that have been replaced (Type:String array),
          "modified":List of paths that have been modifed (Type:String array)
       }


        */


        public void doIndex(StaplerRequest req) throws IOException {
            String payload = IOUtils.toString(req.getReader());
            LOGGER.fine("Full details of the POST was "+payload);

            JSONObject o = JSONObject.fromObject(payload);
            String repoUrl = o.getString("repository_ssl_path");
            LOGGER.info("Received POST for "+repoUrl);

            // run in high privilege to see all the projects anonymous users don't see.
            // this is safe because when we actually schedule a build, it's a build that can
            // happen at some random time anyway.

            // TODO replace with ACL.impersonate as LTS is > 1.461
            Authentication old = SecurityContextHolder.getContext().getAuthentication();
            SecurityContextHolder.getContext().setAuthentication(ACL.SYSTEM);
            try {
                for (AbstractProject<?,?> job : Jenkins.getInstance().getAllItems(AbstractProject.class)) {
                    boolean found = false;
                    SCM scm = job.getScm();
                    if (scm instanceof SubversionSCM) {
                        found = hasRepository(repoUrl, (SubversionSCM) scm);
                    } else if (scm instanceof GitSCM) {
                        found = hasRepository(repoUrl, (GitSCM) scm);
                    } else if (scm instanceof MercurialSCM) {
                        found = hasRepository(repoUrl, (MercurialSCM) scm);
                    }

                    if (found) {
                        LOGGER.info(job.getFullDisplayName()+" triggered by web hook.");
                        job.scheduleBuild(new WebHookCause());
                    }

                    LOGGER.fine("Skipped "+job.getFullDisplayName()+" because it doesn't have a matching repository.");
                }
            } finally {
                SecurityContextHolder.getContext().setAuthentication(old);
            }
        }

    private boolean hasRepository(String repoUrl, SubversionSCM scm) {
        for (SubversionSCM.ModuleLocation location : ((SubversionSCM) scm).getLocations()) {
            if (location.getURL().equals(repoUrl)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasRepository(String repoUrl, GitSCM scm) {
        for (RemoteConfig remote: ((GitSCM) scm).getRepositories()) {
            for (URIish uri : remote.getURIs()) {
                if (uri.toString().equals(repoUrl)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasRepository(String repoUrl, MercurialSCM scm) {
        return scm.getSource().equals(repoUrl);
    }

    private static final Logger LOGGER = Logger.getLogger(WebHook.class.getName());

    }
