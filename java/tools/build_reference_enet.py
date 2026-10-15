"""Build only the vendored native ENet oracle for development/CI interoperability.
This is never invoked by the Java runtime or normal Gradle build.
"""
import pathlib
from setuptools import Extension,setup
from Cython.Build import cythonize
ROOT=pathlib.Path(__file__).resolve().parents[2]
BUILD=ROOT/'java/build/native-reference'
source=ROOT/'vendor/pyenet'
setup(name='battlespades-enet-test-oracle',version='0',script_args=['build_ext','--build-lib',str(ROOT/'java/build/reference-python'),'--build-temp',str(BUILD/'obj')],
      ext_modules=cythonize([Extension('enet',[str(source/'enet.pyx'),*[str(p) for p in sorted((source/'enet').glob('*.c'))]],
          include_dirs=[str(source/'enet/include')],define_macros=[(name,None) for name in ['HAS_POLL','HAS_FCNTL','HAS_MSGHDR_FLAGS','HAS_SOCKLEN_T','HAS_GETHOSTBYNAME_R','HAS_GETHOSTBYADDR_R']])],
          build_dir=str(BUILD/'cython'),compiler_directives={'language_level':'3'}))
