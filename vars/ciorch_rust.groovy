def call(Map args = [:]) {
    ciorch([adapter: 'rust'] + args)
}
