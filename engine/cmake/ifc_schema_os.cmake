# IfcOpenShell (CMAKE_PROJECT_INCLUDE of engine/build_ifcopenshell.sh): compile the generated schema tables
# (src/ifcparse/Ifc*-schema.cpp, one huge initialiser each, run once when a schema is first used) for size.
# At -O3 each needs ~2.6 GB and ~5 min, at -Os ~0.6 GB and 25 s.
include_guard(GLOBAL)
function(_mobilecad_ifc_schema_os)
  get_target_property(src IfcParse SOURCES)
  get_target_property(dir IfcParse SOURCE_DIR)
  list(FILTER src INCLUDE REGEX "-schema\\.cpp$")
  list(TRANSFORM src PREPEND "${dir}/")
  set_source_files_properties(${src} TARGET_DIRECTORY IfcParse PROPERTIES COMPILE_OPTIONS -Os)
endfunction()
cmake_language(DEFER DIRECTORY ${CMAKE_SOURCE_DIR} CALL _mobilecad_ifc_schema_os)
