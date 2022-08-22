PLANTUML = java -jar ~/lib/plantuml-1.2022.6.jar

all: state_diagram.png context_diagram.png

%.png: %.txt
	${PLANTUML} $<


