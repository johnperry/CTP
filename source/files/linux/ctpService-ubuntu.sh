#!/bin/bash

### BEGIN INIT INFO
# Provides: ctp-service
# Should-Start: postgresql
# Should-Stop: postgresql
# Default-Start: 2 3 4 5
# Default-Stop: 0 1 6
# Short-Description: starts the ctp-service
### END INIT INFO


################################################
# Author: SG Langer 12/11/2012
#	revised 5/21/2014 (fixed grep lines in start()
#	and stop()
#
# Purpose: used to start/stop CTP on SysV family *NIX
#	For Ubuntu UpStart equivalent see
#	http://mircwiki.rsna.org/index.php?title=Running_CTP_as_a_Linux_Service
#
# Usage: Assuming that this script lives in CTP_HOME, then
#	a) make a symbolic link to this in /etc/init.d like this
#		> ln -s /CTP_HOME/linux/ctpService.sh /etc/init.d/ctpService
#	b) then add the new service to boot via this
#		> update-rc.d ctpService defaults 98 02
#
#	CTP will now be controllable like any other service.
#	The parameters 98 02 tell the operating system to start
#	CTP very late in the boot process and to kill it very
#	early in the shutdown process. This is done because no
#	other services make use of CTP directly.
###################################################

clear

USER=edge
CTP_HOME=${CTP_HOME}
JAVA_BIN=${JAVA_BIN}
JAVA=$JAVA_BIN/java
CTP_PID=$CTP_HOME/ctp.pid

. /etc/rsna.conf
export RSNA_ROOT
export OPENAM_URL

. /lib/lsb/init-functions

add_to_path() {
#####################################
# Purpose: check if a substr exists in PATH
#	Add if not
#
# $1 = Substr to look for in path
#####################################
  echo "original path"
  echo $PATH

  if [[ "$PATH" =~ (^|:)"$1"(:|$) ]]; then
	echo "PATH contains test string, returning"
	return 0
  else
	echo "PATH does not contain test string, adding"
	export PATH=$PATH:$1
	echo "New path"
	echo $PATH
  fi
}

start() {
################################
# Purpose: start CTP and record PID
#
#################################
	log_daemon_msg "Start CTP"
	echo "in stop"
	ret=$(ps aux |grep libraries |grep -v grep)
	if [[ "$ret" =~ "CTP" ]]; then
		echo "CTP already running, stop first"
		return 0
	fi

        if start-stop-daemon --start -v -c $USER -d $CTP_HOME -m -b --pidfile $CTP_PID --startas $JAVA -- -jar $CTP_HOME/Runner.jar; then

	    echo "CTP started"
        else
            log_end_msg 1
	    echo "CTP did not start"
            exit 1
	fi

       log_end_msg 0
	
	# this PID is not useful becuase Runner.jar creates a sub-process
	#pid=$!
	#echo $pid>$CTP_HOME/pid
}

stop() {
###################################
# Purpose: kill the PID of the CTP job
#	Unfortunetly, the pid recorded in
#	in start() was the parent process
#	and not the real one that Runner
#	launched
#
#	So we have to do a little work
#
##################################
	echo "in stop"
	ret=$(ps aux |grep libraries |grep -v grep)
	if [[ "$ret" =~ "CTP" ]]; then
		echo "CTP is running"
		array=($ret)
		#echo $ret
		#echo ${array[1]}
		result=$(kill ${array[1]})
		echo $result
		exit
	else
		echo "CTP is not running"
	fi
}

status() {
######################################
# Purpose: Print the JobID from the ps cmd
#
####################################
	echo "in status"
	ret=$(ps aux |grep libraries)
	if [[ "$ret" =~ "CTP" ]]; then
		echo "CTP is running"
		echo $ret
	else
		echo "CTP is not running"
	fi
}

############################
# Main
# Purpose: execute cmmd line args
# 
# $1 command line arg for Case
#############################
cd $CTP_HOME
add_to_path $JAVA_BIN

case $1 in
	start)
		start
	;;
	stop)
		stop
	;;
	restart)
		stop
		start
	;;
	status)
		status
	;;
	*)
		echo "invalid option"
		echo "valid options: start/stop/restart/status"
	;;
esac


exit
