PLANTUML = java -jar ~/lib/plantuml-1.2022.6.jar

DIAGRAMS = state_diagram.txt context_diagram.txt container_diagram.txt roles.txt

all: ${DIAGRAMS:.txt=.png}

%.png: %.txt
	${PLANTUML} $<


