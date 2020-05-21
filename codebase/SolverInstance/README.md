# SOLVER INSTANCE
If this is being ran in the RNL-VM:

1. Put base directory cnv-project19-20/ in home folder like so: ~/cnv-project19-20/
    1. The home folder is /home/test/
    2. If it is not the case then cnv-project19-20/codebase/SolverInstance/BIT/config-bit.sh needs to be updated accordingly.
2. Go to cnv-project19-20/codebase/SolverInstance
3. Run 'source BIT/java-config-rnl-vm.sh'
4. Run 'source BIT/config-bit.sh'
5. Compile and instrument code
    1. To run without WebServer run 'source BIT/compile-local.sh'
    2. To run with WebServer run 'source BIT/compile.sh'
6. Execute whatever was chosen in 5
