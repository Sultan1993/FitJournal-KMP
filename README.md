# This is a repository that holds Kotlin Multiplatform code for Fit Journal application.

## Cloning and configuring

This repository contains git submodule inside "swiftpackage" folder. This folder is a separate 
sub-repository that pushes Swift Package to a corresponding repository (https://github.com/Sultan1993/FitJournal-SPM).

So after you clone the project, you need to initialize submodules (https://git-scm.com/book/en/v2/Git-Tools-Submodules).
After "git clone" ->
- git submodule init
- git submodule update

There is another way to clone this repository with all submodules included. Run the cloning with 
"--recurse-submodules" parameter.
- git clone --recurse-submodules
