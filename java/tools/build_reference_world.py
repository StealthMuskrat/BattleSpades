"""Build only the three original Cython movement oracle modules, for development/CI.

Never called by Gradle or the production JAR. Strict compiler flags match setup.py.
Pass --domain to include the original packet/byte codecs and KV6 reader for scene-builder fixtures.
The in-place extensions are ignored development outputs, not production dependencies.
"""
import os
import sys
import pathlib
from setuptools import Extension, setup
from Cython.Build import cythonize

ROOT=pathlib.Path(__file__).resolve().parents[2]
os.chdir(ROOT)
BUILD=ROOT/'java/build/world-reference'
setup(name='battlespades-world-test-oracle',version='0',
      script_args=['build_ext','--inplace','--build-temp',str(BUILD/'obj'),'--build-lib',str(BUILD/'lib')],
      ext_modules=cythonize([
          Extension(name,[name.replace('.','/')+'.pyx'],
                    extra_compile_args=['-O3','-fno-fast-math','-ffp-contract=off'])
          for name in (['shared.bytes','shared.packet','aoslib.kv6'] if '--domain' in sys.argv else [])+['shared.glm','aoslib.vxl','aoslib.world']
      ],build_dir=str(BUILD/'cython'),force=True,
          compiler_directives={'language_level':'3','boundscheck':False,'wraparound':False,'cdivision':True}))
