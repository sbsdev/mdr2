PLANTUML = java -jar ~/lib/plantuml.jar

DIAGRAMS = state_diagram.txt context_diagram.txt container_diagram.txt roles.txt

all: ${DIAGRAMS:.txt=.png}

%.png: %.txt
	${PLANTUML} $<


