def call(Map args = [:]) {
    ciorch(args + [adapter: 'java', matrix: 'java-standard'])
}
