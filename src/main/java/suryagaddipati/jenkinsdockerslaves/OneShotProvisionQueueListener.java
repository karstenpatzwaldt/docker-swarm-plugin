package suryagaddipati.jenkinsdockerslaves;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.queue.QueueListener;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

/**
 * {@link Cloud} API is designed to launch virtual machines, which is an heavy process, so relies on
 * {@link  NodeProvisioner} to determine when a new slave is required. Here we want the slave to start just as a job
 * enter the build queue. As an alternative we listen the Queue for Jobs to get scheduled, and when label match
 * immediately start a fresh new container executor with a unique label to enforce exclusive usage.
 *
 */
@Extension
public class OneShotProvisionQueueListener extends QueueListener {

    @Override
    public void onEnterBuildable(final Queue.BuildableItem bi) {
        if (bi.task instanceof AbstractProject) {
            AbstractProject job = (AbstractProject) bi.task;
            List<String> labels = DockerSlaveConfiguration.get().getLabels();
            if(! labels.contains( job.getAssignedLabel().getName())){
                return;
            }


            try {
                LOGGER.info("Creating a Container slave to host " + job.toString() + "#" + job.getNextBuildNumber());
                DockerLabelAssignmentAction action = createLabelAssignmentAction();
                bi.addAction(action);

                // Immediately create a slave for this item
                // Real provisioning will happen later

                final Node node = new DockerSlave(job, action.getLabel().toString());
                Computer.threadPoolForRemoting.submit(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Jenkins.getInstance().addNode(node);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
            } catch (Descriptor.FormException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onLeft(Queue.LeftItem li) {
        if(li.isCancelled()){
            DockerLabelAssignmentAction labelAssignmentAction = li.getAction(DockerLabelAssignmentAction.class);
            if (labelAssignmentAction !=null){
                String computerName = labelAssignmentAction.getLabel().getName();

                final Node node = Jenkins.getInstance().getNode(computerName);
                Computer.threadPoolForRemoting.submit(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Jenkins.getInstance().removeNode(node);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        }
    }

    private DockerLabelAssignmentAction createLabelAssignmentAction() {
        try {
            Thread.sleep(5,10);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        final String id = Long.toHexString(System.nanoTime());
        final Label label = new DockerMachineLabel(id);
        return new DockerLabelAssignmentAction(label);
    }

    private static final Logger LOGGER = Logger.getLogger(OneShotProvisionQueueListener.class.getName());
}
