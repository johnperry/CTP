#!/bin/bash
# chkconfig:		2345 75 15
# description: 		ctp-service
### BEGIN INIT INFO
# Provides:		ctp-service
# Required-Start:	$all
# Required-Stop:	$all
# Default-Start:	2 3 4 5
# Default-Stop:		0 1 6
### END INIT INFO

################################################
# Author: SG Langer 12/11/2012
#
# Purpose: used to start/stop CTP on SysV family *NIX
#	For Ubuntu UpStart equivalent see
#	http://mircwiki.rsna.org/index.php?title=Running_CTP_as_a_Linux_Service
#
# Usage: Assuming that this script lives in CTP_HOME, then
#	a) make a symbolic link to this in /etc/init.d like this 
#		> ln -s /CTP_HOME/ctp-service ctpService
#	b) then add the new service to boot via this
#		> chkconfig --add ctpService
#
#	CTP will now be controllable like any other 
#	service
###################################################

clear
# Source function library.
. /etc/rc.d/init.d/functions

JAVA_HOME="/usr/java/default"
JAVA_BIN="/usr/java/default/bin"
CLASSPATH=".:/usr/java/default/jre/lib:/usr/java/default/lib:/usr/java/default/lib/tools.jar"
CTP_HOME="/opt/CTP"

add_to_path() {
######################################
# Purpose: check if a substr exists in PATH
#	Add if not
#
# $1 = Substr to look for in path
######################################
  #echo "original path"
  #echo $PATH

  if [[ "$PATH" =~ (^|:)"$1"(:|$) ]]; then
	#echo "PATH does contain test string, returning"
	return 0
  else
	#echo "PATH does not containt test string, adding"
	export PATH=$PATH:$1
	#echo "New path"
	#echo $PATH
  fi
}


start() {
################################
# Purpose: start CTP and record PID
#
#################################
	echo "in start"
	if [ -f $CTP_HOME/pid ]; then
		echo "CTP already running, stop first"
		return 0
	fi
	#eval "( java -Dinstall4j.jvmDir="$JAVA_HOME" -classpath "$CLASSPATH" -jar $CTP_HOME/Runner.jar) &"
	eval "( java  -classpath "$CLASSPATH" -jar $CTP_HOME/Runner.jar) &"
	pid=$!
	echo $pid>$CTP_HOME/pid
}

stop() {
###################################
# Purpose: kill the PID of the CTP job
#	Unfortunatly, the pid recorded in
#	in start() was the parent process
#	and not the real one that Runner
#	launched
#
#	So we have to do a little work
#
##################################
	echo "in stop"
	if [ ! -f $CTP_HOME/pid ]; then
		echo "CTP is not running"
		return 0
	fi 

	text=$(ps aux |grep libraries )
	array=($text)
	#echo $text
	#echo ${array[1]}
	
	result=$(kill ${array[1]})
	echo $result
	rm $CTP_HOME/pid
}

status() {
######################################
# Purpose: Print the JobID from the ps cmd
#
####################################
	echo "in status"
	ret=$(ps aux |grep libraries)
	if [[ "$ret" =~ "java" ]]; then
		echo "CTP is running"
		echo $ret
	else
		echo "CTP is not running"
	fi
}


cd $CTP_HOME
add_to_path $JAVA_BIN

############################
# Main
# Purpose: execute cmmd line args
# 
# $1 command line arg for Case
#############################

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

