def call(Map args = [:]) {
    ciorch(args + [adapter: 'php', matrix: 'php-standard'])
}
