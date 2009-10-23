#!/bin/sh -f

#
# Script to bundle BDVAL and required data into a
# suitable format for submission to a PBS queue
#

if [ -z $1 ]; then
    echo "Job name is required"
    exit 1
fi

# Absolute path to this script.
SCRIPT=$(readlink -f $0)
# Absolute path this script is in.
SCRIPT_DIR=`dirname $SCRIPT`

# Local bdval locations
BDVAL_DIR=${BDVAL_DIR:-$SCRIPT_DIR/../..}
BDVAL_CONFIG_DIR=${BDVAL_DIR}/config
BDVAL_DATA_DIR=${BDVAL_DIR}/data

if [ ! -e ${BDVAL_DIR}/bdval.jar ]; then
    echo "bdval.jar not found!"
    exit 2
fi

# The job name identifies which scipts to pull in
JOB=$1

# Create a somewhat unique tag but limit it to 15 characters (PBS name limit)
PID=$$
JOB_TAG=${JOB:0:15-${#PID}}$PID
JOB_DIR=$(readlink -f .)/${JOB_TAG}
JOB_CONFIG_DIR=${JOB_DIR}/config
JOB_DATA_DIR=${JOB_DIR}/data

JOB_RESULTS_DIR=${JOB_RESULTS_DIR:-$JOB_DIR/results}

if [ ! -e ${JOB}-pbs-env.sh ]; then
    echo "${JOB}-pbs-env.sh not found!"
    exit 3
fi

. ${JOB}-pbs-env.sh

echo "Bundling job submission files"

# Bundle the files required for the job submission
/bin/mkdir -p ${JOB_DIR} ${JOB_CONFIG_DIR} ${JOB_DATA_DIR} ${JOB_RESULTS_DIR}
/bin/cp -p ${SCRIPT_DIR}/start-rserve.sh ${JOB_DIR}
/bin/cp -p ${BDVAL_DIR}/bdval.jar ${JOB_DIR}
/bin/cp -pr ${BDVAL_DIR}/buildsupport ${JOB_DIR}
/bin/cp -p ${BDVAL_DATA_DIR}/bdval.xml ${BDVAL_DATA_DIR}/${JOB}.xml ${BDVAL_DATA_DIR}/${JOB}.properties ${JOB_DATA_DIR}
/bin/cp -pr ${BDVAL_DATA_DIR}/sequences ${BDVAL_DATA_DIR}/gene-lists ${JOB_DATA_DIR}
/bin/cp -pr ${DATASET_ROOT} ${JOB_DATA_DIR}/${JOB}-data

if [ -e ${BDVAL_CONFIG_DIR}/log4j.properties ]; then
    /bin/cp -p ${BDVAL_CONFIG_DIR}/log4j.properties ${JOB_CONFIG_DIR}
fi

echo "Creating submission script"

#
# Tell bdval not to try and compile when running
#
cat > ${JOB_DATA_DIR}/bdval.properties <<EOF
use-bdval-jar=true
nocompile=true
EOF

#
# Two instances of Rserve per node
#
cat > ${JOB_CONFIG_DIR}/RConnectionPool.xml <<EOF
<RConnectionPool>
   <RConfiguration>
      <RServer host="localhost" port="6311"/>
      <RServer host="localhost" port="6312"/>
   </RConfiguration>
</RConnectionPool>
EOF

#
# Two threads per node
#
cat > ${JOB_CONFIG_DIR}/${JOB}-local.properties <<EOF
eval-dataset-root=${JOB}-data
computer.type=server
server.thread-number=2
server.memory=-Xmx2000m
EOF

#
# Create the actual job script that will launch bdval
#
cat > ${JOB_DIR}/bdval-pbs.sh <<EOF
#!/bin/sh
. /etc/profile

# Absolute path to this script.
SCRIPT=\$(readlink -f \$0)
# Absolute path this script is in.
SCRIPT_DIR=\`dirname \$SCRIPT\`

# Run bdval job
cd \$SCRIPT_DIR/data
ant -Dsave-data-tag="${SAVE_DATA_TAG}" -Dtag-description="${TAG_DESCRIPTION}" -f ${JOB}.xml ${ANT_TARGET}

# Copy results back to the master node when we are done
JOB_RESULTS_DIR=${JOB_RESULTS_DIR}/\${HOSTNAME}
ssh master01 "/bin/mkdir -p \${JOB_RESULTS_DIR}"
scp -r *.zip logs @master01:\${JOB_RESULTS_DIR}
EOF

chmod u+x ${JOB_DIR}/bdval-pbs.sh

#
# Create the job submission script
#
cat > ${JOB_TAG}.qsub <<EOF
#/bin/sh -x

# Determines the queue a job is submitted to
#PBS -q ${PBS_QUEUE}

# Name of the job
#PBS -N ${JOB_TAG}

# Combine PBS error and output files.
#PBS -j oe

# Save PBS output to the result directory
#PBS -o ${JOB_RESULTS_DIR}/${JOB_TAG}.log

# Number of nodes (exclusive access)
#PBS -l nodes=$PBS_NODES#excl

# Mail job status at completion
#PBS -m ae

# Mail to user specified
#PBS -M $MAILTO

#
# Output some useful job information
#
echo ------------------------------------------------------
echo Job is running on the following nodes:
cat \$PBS_NODEFILE
echo ------------------------------------------------------
echo PBS: qsub is running on \$PBS_O_HOST
echo PBS: originating queue is \$PBS_O_QUEUE
echo PBS: executing queue is \$PBS_QUEUE
echo PBS: working directory is \$PBS_O_WORKDIR
echo PBS: execution mode is \$PBS_ENVIRONMENT
echo PBS: job identifier is \$PBS_JOBID
echo PBS: job name is \$PBS_JOBNAME
echo PBS: node file is \$PBS_NODEFILE
echo PBS: current home directory is \$PBS_O_HOME
echo PBS: PATH = \$PBS_O_PATH
echo ------------------------------------------------------

# Copy needed files from master node to each worker
for node in \`sort \$PBS_NODEFILE | uniq\`
do
  echo "=============================================="
  echo "Copying files to \$node"
  echo "=============================================="
  scp -pr @master01:$JOB_DIR @\$node:\$TMPDIR
done

#
# Start instances of Rserve on each node in the cluster
#
echo "=============================================="
echo "Starting Rserve instances"
echo "=============================================="
pbsdsh -v \$TMPDIR/$JOB_TAG/start-rserve.sh 6311
pbsdsh -v \$TMPDIR/$JOB_TAG/start-rserve.sh 6312

# TODO - remove this block it's just for testing (bdval will do this anyway)
for node in \`sort \$PBS_NODEFILE | uniq\`
do
  ssh \$node "java -jar /home/marko/RUtils/icb-rutils.jar --port 6311 --validate"
  ssh \$node "java -jar /home/marko/RUtils/icb-rutils.jar --port 6312 --validate"

  ssh \$node "ps -elf | grep \$USER"
done

# launch bdval on each node
pbsdsh -v \$TMPDIR/$JOB_TAG/bdval-pbs.sh

# TODO - remove this block - just double checking stuff here
for node in \`sort \$PBS_NODEFILE | uniq\`
do
  ssh \$node "ls -R /tmp"
done
EOF

echo
echo "Job tag is $JOB_TAG"
echo "Submit the job by executing \"qsub $JOB_TAG.qsub\""
echo "Once submitted check status with \"qstat -fnu $USER\""
echo "Results will placed in ${JOB_RESULTS_DIR}"
