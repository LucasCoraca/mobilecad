# Injected into FreeCAD's configure (CMAKE_PROJECT_INCLUDE) for the headless Android build.
# Qt LinguistTools (qttools) is not built for Android; the engine ships without .qm translations.
if(NOT COMMAND qt_add_translation)
  function(qt_add_translation out)
    set(${out} "" PARENT_SCOPE)
  endfunction()
endif()
if(NOT COMMAND qt_create_translation)
  function(qt_create_translation out)
    set(${out} "" PARENT_SCOPE)
  endfunction()
endif()

# TechDraw's App library uses QtGui/QtWidgets types (QColor, QPen, QApplication::translate) even in headless builds.
find_package(Qt6 REQUIRED COMPONENTS Gui Widgets)
set(QtGui_INCLUDE_DIR ${Qt6Gui_INCLUDE_DIRS})
set(QtWidgets_INCLUDE_DIRS ${Qt6Widgets_INCLUDE_DIRS} ${Qt6Gui_INCLUDE_DIRS})
set(QtWidgets_LIBRARIES Qt6::Widgets Qt6::Gui)
